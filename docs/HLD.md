# High-Level Design (HLD): Agile Sprint Board

## 1. Purpose
A lightweight Jira-style tool to run agile sprints: plan a sprint, assign stories to developers, and track each story through the delivery workflow until Product Owner (PO) acceptance.

## 2. Goals / Non-Goals
**Goals**
- Sprint-based board with a fixed six-stage workflow.
- Assign and reassign developers per story.
- Real-time visibility of sprint progress (stories, accepted points).
- Simple to run: one deployable JAR, zero external dependencies.

**Non-Goals (v1)**
- Authentication / RBAC, notifications, audit history, attachments, comments, multi-tenancy, burn-down charts.

## 3. Users and Roles (logical, not yet enforced)
| Role | Responsibility |
|---|---|
| Developer | Moves stories In Progress → Dev Ready → Dev Completed |
| QA | Moves stories QA Ready → QA Completed |
| Product Owner | Gives final PO Acceptance |
| Scrum Master | Creates sprints, manages developers and assignments |

## 4. Workflow (State Model)
`IN_PROGRESS → DEV_READY → DEV_COMPLETED → QA_READY → QA_COMPLETED → PO_ACCEPTANCE`

- New stories start in `IN_PROGRESS`.
- v1 allows any move (including backwards) via drag and drop; a story in `PO_ACCEPTANCE` counts as accepted points.
- v2 enforces transitions and role permissions (see section 12).

## 5. Functional Requirements
| ID | Requirement |
|---|---|
| FR-1 | Create and list sprints (name, goal, start/end date, active flag) |
| FR-2 | Create and list developers |
| FR-3 | Create stories (title, description, points) inside a sprint |
| FR-4 | Assign / reassign a developer to a story |
| FR-5 | Move a story between the six workflow states |
| FR-6 | Filter board by developer |
| FR-7 | Show sprint stats: story count, accepted vs total points, progress bar |
| FR-8 | Delete a story |

## 6. Non-Functional Requirements
| Area | Target |
|---|---|
| Performance | Board load under 300 ms for up to 500 stories per sprint |
| Availability | Single node v1; stateless app tier allows horizontal scaling in v2 |
| Persistence | File-based H2 (v1); PostgreSQL (v2) |
| Security | v1 internal/trusted network only; v2 OAuth2/OIDC + RBAC |
| Maintainability | Layered code, REST contract documented in `API.md` |
| Portability | Java 17, Maven, single JAR; Docker-ready |

## 7. Architecture Summary
Three logical tiers, packaged as one Spring Boot application:

1. **Presentation:** static single-page UI (HTML, CSS, vanilla JS) served by Spring Boot from `/static`.
2. **Application:** Spring MVC REST controller (`/api/**`) containing board logic.
3. **Data:** Spring Data JPA repositories over an H2 file database.

See `ARCHITECTURE.md` for diagrams.

## 8. Key Components
| Component | Responsibility |
|---|---|
| `index.html` (SPA) | Renders board, drag and drop, dialogs, calls REST API with `fetch` |
| `BoardController` | REST endpoints for sprints, developers, stories, status and assignee changes |
| `SprintRepo`, `DevRepo`, `StoryRepo` | CRUD and `findBySprintId` via Spring Data JPA |
| Entities `Sprint`, `Developer`, `Story` | Persistent domain model |
| `Status` enum | Defines the workflow stages |
| `CommandLineRunner seed` | Loads demo data on first start |

## 9. Data Design (summary)
- `Sprint(id, name, goal, startDate, endDate, active)`
- `Developer(id, name)`
- `Story(id, title, description, points, status, sprintId, assigneeId)`
- Relationships are held as plain id references (`sprintId`, `assigneeId`) to keep JSON simple and avoid lazy-loading problems. v2 moves to proper foreign keys.

Full ER diagram in `ARCHITECTURE.md`.

## 10. Interface Design
REST/JSON over HTTP. Resource-oriented; status and assignee changes use `PATCH`. Details in `API.md`.

## 11. Key Design Decisions
| Decision | Rationale | Trade-off |
|---|---|---|
| Single deployable (UI served by Spring Boot) | No CORS, one build, easy run | UI and API release together |
| Vanilla JS, no framework | Zero build step, fast to run | Harder to scale UI complexity (migrate to React later) |
| H2 file DB | No setup, persists locally | Not for multi-node production |
| Id references instead of JPA relations | Simple JSON, no N+1 or lazy issues | No DB-enforced referential integrity |
| Any-to-any status moves | Fast v1 usability | No workflow governance yet |

## 12. Evolution Roadmap
1. **Governance:** enforce allowed transitions and role checks; transition audit log.
2. **Security:** Keycloak (OIDC), Spring Security, JWT, roles Dev / QA / PO / SM.
3. **Data:** PostgreSQL, Flyway migrations, real foreign keys, indexes on `story(sprint_id, status)`.
4. **Events:** publish `StoryStatusChanged` to Kafka for notifications and analytics.
5. **Real-time:** WebSocket/SSE so multiple users see board updates live.
6. **Reporting:** burn-down chart, velocity, cycle time per stage.
7. **UI:** React + TypeScript frontend, optimistic updates.
8. **Ops:** Docker image, Kubernetes deployment, CI/CD pipeline, Actuator + Prometheus.

## 13. Risks
| Risk | Mitigation |
|---|---|
| Concurrent edits overwrite each other | Add `@Version` optimistic locking in v2 |
| No auth exposes data | Run only on trusted network until v2 security |
| H2 file corruption / single node | Move to PostgreSQL with backups |
