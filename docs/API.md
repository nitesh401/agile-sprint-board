# REST API Reference

Base URL: `http://localhost:8080/api`. All bodies are JSON.

## Enum: `Status`
`IN_PROGRESS`, `DEV_READY`, `DEV_COMPLETED`, `QA_READY`, `QA_COMPLETED`, `PO_ACCEPTANCE`

## Sprints
| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/sprints` | none | List all sprints |
| POST | `/sprints` | `{name, goal, startDate, endDate, active}` | Create sprint |

## Developers
| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/developers` | none | List developers |
| POST | `/developers` | `{name}` | Create developer |

## Stories
| Method | Path | Body | Description |
|---|---|---|---|
| GET | `/stories?sprintId={id}` | none | Stories in a sprint |
| POST | `/stories` | `{title, description, points, sprintId, assigneeId, status?}` | Create story (default `IN_PROGRESS`) |
| PUT | `/stories/{id}` | full story | Replace story |
| PATCH | `/stories/{id}/status` | `{"status": "QA_READY"}` | Move to a workflow state |
| PATCH | `/stories/{id}/assignee` | `{"assigneeId": 2}` (null to unassign) | Assign developer |
| DELETE | `/stories/{id}` | none | Delete story |

## Example
```bash
curl -X POST localhost:8080/api/stories -H 'Content-Type: application/json' \
  -d '{"title":"Payment API","description":"REST endpoints","points":5,"sprintId":1,"assigneeId":1}'

curl -X PATCH localhost:8080/api/stories/1/status -H 'Content-Type: application/json' \
  -d '{"status":"DEV_READY"}'
```

## Story response
```json
{"id":1,"title":"Payment API","description":"REST endpoints","points":5,
 "status":"DEV_READY","sprintId":1,"assigneeId":1}
```

## Error behaviour (v1)
Spring defaults: `400` for malformed JSON or invalid enum value, `500` when an id is not found. A structured error model and `404` handling are planned for v2.
