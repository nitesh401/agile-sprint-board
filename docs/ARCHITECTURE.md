# Architecture Design

Diagrams use Mermaid (rendered by GitHub, GitLab, VS Code Markdown preview).

## 1. System Context
```mermaid
flowchart LR
    Dev[Developer] --> App
    QA[QA Engineer] --> App
    PO[Product Owner] --> App
    SM[Scrum Master] --> App
    App[Agile Sprint Board<br/>Spring Boot app] --> DB[(H2 / PostgreSQL)]
```

## 2. Container / Component View (current v1)
```mermaid
flowchart TB
    subgraph Browser
        UI[SPA: index.html<br/>board, drag and drop, dialogs]
    end
    subgraph SpringBoot[Spring Boot JVM]
        Static[Static resource handler]
        Ctl[BoardController<br/>/api/**]
        Repo[Spring Data JPA repositories<br/>SprintRepo, DevRepo, StoryRepo]
        Seed[Seed CommandLineRunner]
    end
    DB[(H2 file DB<br/>./data/agiledb)]
    UI -- GET / --> Static
    UI -- fetch JSON --> Ctl
    Ctl --> Repo --> DB
    Seed --> Repo
```

## 3. Layered Design
| Layer | Technology | Notes |
|---|---|---|
| UI | HTML, CSS, JS | Re-renders board from API state |
| Web / API | Spring MVC `@RestController` | JSON, stateless |
| Persistence | Spring Data JPA + Hibernate | `ddl-auto=update` in v1 |
| Storage | H2 file database | Replace with PostgreSQL in v2 |

## 4. Data Model (ER)
```mermaid
erDiagram
    SPRINT ||--o{ STORY : contains
    DEVELOPER ||--o{ STORY : "assigned to"
    SPRINT {
        long id PK
        string name
        string goal
        date startDate
        date endDate
        boolean active
    }
    DEVELOPER {
        long id PK
        string name
    }
    STORY {
        long id PK
        string title
        string description
        int points
        string status
        long sprintId FK
        long assigneeId FK
    }
```

## 5. Workflow State Machine
```mermaid
stateDiagram-v2
    [*] --> IN_PROGRESS: story created
    IN_PROGRESS --> DEV_READY
    DEV_READY --> DEV_COMPLETED
    DEV_COMPLETED --> QA_READY
    QA_READY --> QA_COMPLETED
    QA_COMPLETED --> PO_ACCEPTANCE
    PO_ACCEPTANCE --> [*]: accepted
```
v1 also permits backward moves (e.g. QA rejects, back to IN_PROGRESS).

## 6. Sequence: Drag a Card to a New Column
```mermaid
sequenceDiagram
    actor U as User
    participant UI as SPA
    participant C as BoardController
    participant R as StoryRepo
    participant D as Database
    U->>UI: drop card on "QA Ready"
    UI->>C: PATCH /api/stories/{id}/status {status: QA_READY}
    C->>R: findById(id)
    R->>D: SELECT
    C->>R: save(story)
    R->>D: UPDATE
    C-->>UI: 200 updated story
    UI->>C: GET /api/stories?sprintId=
    C-->>UI: stories[]
    UI-->>U: re-render board and stats
```

## 7. Sequence: Create Story and Assign Developer
```mermaid
sequenceDiagram
    actor U as Scrum Master
    participant UI as SPA
    participant C as BoardController
    U->>UI: fill story form
    UI->>C: POST /api/stories
    C-->>UI: 200 story (IN_PROGRESS)
    U->>UI: pick developer on card
    UI->>C: PATCH /api/stories/{id}/assignee
    C-->>UI: 200 story
```

## 8. Deployment View
**v1 (current):** one JVM process.
```mermaid
flowchart LR
    B[Browser] -->|HTTP 8080| J[agile-board.jar<br/>Spring Boot]
    J --> F[(./data/agiledb.mv.db)]
```
**v2 (target):**
```mermaid
flowchart LR
    B[Browser] --> I[Ingress / Load Balancer]
    I --> A1[App pod 1]
    I --> A2[App pod 2]
    A1 --> P[(PostgreSQL)]
    A2 --> P
    A1 -.events.-> K[[Kafka]]
    A2 -.events.-> K
    I --> KC[Keycloak OIDC]
    K --> N[Notification service]
```

## 9. Cross-Cutting Concerns
| Concern | v1 | v2 |
|---|---|---|
| AuthN/AuthZ | None | Keycloak + Spring Security, roles Dev/QA/PO/SM |
| Validation | Minimal (UI) | Bean Validation on DTOs, 400 error model |
| Error handling | Spring defaults | `@ControllerAdvice` with problem+json |
| Concurrency | Last write wins | `@Version` optimistic locking, 409 on conflict |
| Observability | Console logs | Actuator, Micrometer, Prometheus, structured logs |
| Testing | None | JUnit 5, MockMvc, Testcontainers (PostgreSQL) |
| Config | `application.properties` | Profiles (dev, prod), env-based secrets |

## 10. Suggested Package Structure (v2 refactor)
```
com.agile.board
 ├─ web         controllers, DTOs, exception handler
 ├─ service     SprintService, StoryService (transition rules)
 ├─ domain      entities, Status enum
 ├─ repo        Spring Data repositories
 ├─ event       StoryStatusChanged, Kafka publisher
 └─ config      security, OpenAPI, CORS
```

## 11. Scalability Notes
- App tier is stateless, so it scales horizontally behind a load balancer once the DB is shared.
- Main query is `findBySprintId`; add an index on `story(sprint_id)` and `(sprint_id, status)`.
- For live boards, use SSE/WebSocket fan-out (Redis pub/sub when multi-node).
