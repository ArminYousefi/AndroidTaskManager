# Architecture

## Overview
```
 Android app (Compose, Hilt)                         Ktor server
 ┌──────────────────────────┐   REST / JSON           ┌──────────────────────────────┐
 │ presentation (screens +  │ ───────────────────────▶│ routes  (thin: parse + call) │
 │   ViewModels)            │◀── WebSocket events ────│ services (rules, authz, log) │
 │ domain (models, repo     │                         │ domain  (pure rules)         │
 │   interfaces, rules)     │                         │ models  (Exposed tables, DTO)│
 │ data (Room, Retrofit,    │                         │ plugins (errors, DB)         │
 │   sync, WebSocket)       │                         └───────────────┬──────────────┘
 └──────────────────────────┘                                         │ PostgreSQL
```

## Shared rules (single source of truth)
`server_app/.../domain/` holds pure Kotlin (no framework): password policy, nickname/username policy, roles &
permission matrix, board templates (default columns), issue keys, ordering, rate limiter. `tools/sync_shared_rules.sh`
copies them to `android_app/.../domain/rules/` (only the `package` line changes). The server enforces; the app uses the
same code to draw the live ✓/✗ password checklist and to hide controls a role cannot use. **Edit rules on the server
side, then run the script.**

## Server
* **Authorisation** - `AccessService.require(boardId, userId, Permission)` is called by every service method. A user's
  effective role = strongest of *direct membership* and *roles of teams attached to the board*. No access at all → `404`
  (existence is not revealed); too weak a role → `403`.
* **Activity log** - services diff old vs new and call `ActivityService.record(...)`; the entry is stored and pushed to
  board members. Logged: board create/rename/update/empty, column create/rename/delete/reorder/WIP/category, task
  create/rename/move/replan/delete/assign/priority/type/description/labels, comments, member and team changes.
  "Replan" = changing start/due date.
* **Ordering** - `POST /tasks/{id}/move` renumbers source and target column on the server; clients send "column + index".
* **Realtime** - `WebSocketHub` delivers an event only to users who can see the board (old version broadcast to everyone).
* **Errors** - `ApiException` subclasses → uniform body `{"error":{code,message,details,requestId}}`; unexpected exceptions →
  HTTP 500 with a generic message, full stack stored in `error_reports` (source `server`). Every response has `X-Request-Id`
  that also appears in the log line.
* **Security** - bcrypt (cost 12, 72-byte limit enforced), failed-login throttle (5/min/username), password-change throttle,
  refresh-token rotation, all refresh tokens revoked on password change, production refuses the default JWT secret,
  diagnostics upload is rate-limited.
* **Schema upgrades** - `SchemaUtils.createMissingTablesAndColumns` + idempotent back-fill (`DatabaseFactory`).

## Android
* **Layers** - `presentation` (Compose + ViewModels) → `domain` (models, repository interfaces, rules) → `data`.
* **Offline-first** - Room is the read model (Flows). Task writes are *optimistic*: local first, then the server.
  Transient failure → queued in `pending_changes` and replayed **in order** by `PendingChangeProcessor`
  (on reconnect, on board refresh, and every 15 min by `SyncWorker`). Permanent rejection (403/404/409/400) → the local
  change is rolled back and the reason is shown. Board/column/member changes need a connection.
* **Error handling** - everything becomes an `AppException(kind, code, userMessage, details, requestId)`.
  `BaseViewModel.launchSafe` → `UiErrorHandler.handle` → (log) + (developer report if SERVER/UNKNOWN) + (snackbar).
  `CrashHandler` writes uncaught crashes to a file; on next start `ErrorReporter` queues them; `ErrorUploadWorker` uploads
  when online. Users can turn reporting off in Settings.
* **Session** - interceptor adds the token; authenticator refreshes on 401 (serialised because refresh tokens rotate; only
  a *rejected* refresh logs the user out, going offline does not). Login/logout navigation is driven by session state.
* **Drag & drop** - `DragController` (state), `DragMath` (pure geometry, unit-tested), floating `DragGhost`, edge
  auto-scroll, insertion line, `CelebrationBurst` when dropping into a Done column.
