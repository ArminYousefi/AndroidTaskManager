# What changed vs. the original project

## Requested features
1. **Settings**: theme Light/Dark/Automatic (persisted, applied live), nickname (username immutable), change password with current password + live ✓/✗ rules box, log out.
2. **Board**: hold-and-move between columns; hold → Rename / Replan / Delete (+ assign, priority, move to); all logged and shown in an **Activity** screen (board) and **History** tab (issue).
3. **Jira-like flow**: default columns, issue keys/types/priorities/dates/labels/comments, WIP limits, board settings page, quick filters, boards list with working ⋮ menu.
4. **Exception handling** with developer reports uploaded to the server.
5. **Multi-user**: teams, per-board roles, server-side authorisation, live updates scoped to members.
6. KDoc on code, Markdown docs, unit + end-to-end tests.

## Bugs found in the original and fixed
* `android_app/gradle/` (version catalog, wrapper) missing from the zip, and the catalog lacked `navigation-compose`.
* `tryFlushPending()` was never called; `SyncWorker` sent every board/list change (even deletes) to *create board*.
* WorkManager default initializer was not removed → `@HiltWorker` classes could never be created.
* Server URL `192.168.1.2` vs. network-security config that only allowed `10.0.2.2`/`localhost` (cleartext blocked on devices); URL is now one build-time value.
* WebSocket broadcast every event to every user; WebSocket URL was hard-coded separately in the ViewModel.
* Refresh logic cleared the session on *any* failure (even offline) and could rotate the refresh token twice concurrently.
* Access-token prefix was written to the log; `allowBackup=true` would restore tokens to other devices.
* `Board.updatedAtText` printed a literal `$minutes`.
* `kotlinOptions {}` (error in Kotlin 2.2) replaced by `compilerOptions`.
* Server returned exception messages to clients (leak), default JWT secret accepted silently, bcrypt silently truncates >72 bytes.
* Dead code removed: conflict-resolution screens that were never wired into navigation, duplicate WebSocket service, legacy auth routes, `.bak` files.

## Behaviour changes to know about
* Local database schema v2 uses destructive migration (it is only a cache; data is re-downloaded).
* Board/column/member changes require a connection; task changes still work offline.
* `/sync` still exists for old clients; the new app uses per-operation endpoints.
* Due dates are *calendar dates* stored as UTC midnight; "overdue" starts the day after.
