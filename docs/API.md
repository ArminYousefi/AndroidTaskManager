# API

Base URL `http://host:8080/`. JSON everywhere. Authenticated routes need `Authorization: Bearer <access token>`.

## Errors
Every non-2xx response:
```json
{"error":{"code":"VALIDATION_ERROR","message":"Password does not meet the requirements",
          "details":{"UPPERCASE":"One uppercase letter (A-Z)"},"requestId":"1b9e…"}}
```
Codes: `VALIDATION_ERROR` 400 · `INVALID_BODY` 400 · `INVALID_CURRENT_PASSWORD` 400 · `UNAUTHORIZED` / `INVALID_CREDENTIALS` / `INVALID_REFRESH_TOKEN` 401 ·
`FORBIDDEN` 403 · `NOT_FOUND` 404 · `CONFLICT` / `USER_EXISTS` / `ALREADY_MEMBER` / `COLUMN_NOT_EMPTY` / `LAST_COLUMN` 409 · `RATE_LIMITED` 429 · `INTERNAL_ERROR` 500.

## Account
| Method | Path | Body → Result |
|---|---|---|
| POST | `/auth/signup` | `{username,password,email?,nickname?}` → `{token,refreshToken,user}` (201) |
| POST | `/auth/login` | `{username,password}` → same |
| POST | `/auth/refresh` | `{refreshToken}` → same (rotates) |
| POST | `/auth/logout` | `{refreshToken?}` → 204 |
| GET / PATCH | `/users/me` | PATCH `{nickname}` → user. **Username cannot change.** |
| POST | `/users/me/password` | `{currentPassword,newPassword}` → new tokens; all other sessions revoked |
| GET | `/users/search?q=` | ≥2 chars → up to 10 users |

Password rules (server-enforced, identical to the app checklist): ≥8 chars · ≤72 bytes · uppercase · lowercase · digit · special · no spaces ·
must not contain the username · must differ from the current password. Nickname: 2-30 chars, letters/digits/space/`. _ ' -`.

## Boards & columns
| Method | Path | Notes |
|---|---|---|
| GET / POST | `/boards` | POST `{id?,name,key?,description?,template?}`; template `STANDARD`(default) `SIMPLE` `KANBAN` |
| GET / PUT / DELETE | `/boards/{id}` | PUT `{name,description}` (admin). GET returns `myRole`, counts |
| POST | `/boards/{id}/empty` | admin; soft-deletes all issues → `{removed}` |
| GET | `/boards/{id}/lists`, `/boards/{id}/tasks` | |
| POST | `/boards/{id}/lists/reorder` | `{ids:[…]}` every column once (admin) |
| POST / PUT / DELETE | `/lists`, `/lists/{id}` | `{boardId,title,category,wipLimit}`; DELETE `?moveTo=<listId>` or `?deleteTasks=true` (admin) |
| GET | `/boards/{id}/activity?limit&before` | newest first |

## People
`/boards/{id}/members` (GET, POST `{username,role}`, PUT/DELETE `/{userId}`) · `/boards/{id}/teams` (GET, POST `{teamId,role}`, PUT/DELETE `/{teamId}`) ·
`/teams` (GET, POST) · `/teams/{id}` (PUT, DELETE) · `/teams/{id}/members` (GET, POST, PUT/DELETE `/{userId}`).
Board roles `VIEWER|MEMBER|ADMIN`, team roles `MEMBER|ADMIN`. The board owner can't be demoted or removed.

## Issues
| Method | Path | Notes |
|---|---|---|
| POST | `/tasks` | client-supplied `id` makes retries idempotent |
| GET / PUT / DELETE | `/tasks/{id}` | PUT diffs the fields and logs rename / replan / assign / priority / … |
| POST | `/tasks/{id}/move` | `{listId,position}` - server renumbers both columns, logs `TASK_MOVED` |
| GET / POST | `/tasks/{id}/comments`, DELETE `/{commentId}` | |
| GET | `/tasks/{id}/activity` | history of one issue |
| POST | `/sync` | legacy bulk upload (older clients) |

## Operations
`GET /health` · `POST /diagnostics/errors` `{reports:[…]}` (token optional, 30 req/min/IP, ≤20 reports) ·
`GET /diagnostics/errors?limit&source=android|server` with header `X-Dev-Key: $DEV_API_KEY` (404 if no key configured).

## WebSocket `/ws`
Same Bearer header. Messages `{type,entity,boardId,data}`; delivered only to users who can see `boardId`.
Types: `task_created|updated|moved|deleted` · `list_created|updated|deleted|reordered` · `board_updated|deleted|emptied` ·
`activity_created` · `member_changed` · `board_membership_changed`.
