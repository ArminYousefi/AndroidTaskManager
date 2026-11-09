# Task Manager - Jira-style, multi-user (Android + Ktor)

> **خلاصه‌ی فارسی:** یک برنامه‌ی مدیریت تسک شبیه Jira با سرور Ktor و اپ اندروید (Compose). چندکاربره با نقش‌های Viewer / Member / Admin، تیم‌ها، لاگ کامل فعالیت‌ها، Drag & Drop با نگه‌داشتن کارت، منوی Rename/Replan/Delete، تنظیمات (تم Light/Dark/Automatic، تغییر Nickname، تغییر پسورد با چک‌لیست زنده)، مدیریت خطا با ارسال گزارش به سرور برای Dev. جزئیات در پوشه‌ی `docs/`.

## What is inside

| Folder | What |
|---|---|
| `server_app/` | Ktor 2.3 + Exposed + PostgreSQL backend (REST + WebSocket) |
| `android_app/` | Kotlin + Jetpack Compose + Hilt + Room + Retrofit/Moshi app |
| `tools/` | `sync_shared_rules.sh` - copies the shared rule files from server to app |
| `docs/` | Architecture, API, design system, testing, change log |

## Features

* **Boards like Jira** - default columns *To Do / In Progress / In Review / Done* (templates: Standard, Simple, Kanban), issue keys (`WEB-12`), types, priorities, due dates, labels, comments, WIP limits, quick filters.
* **Hold & move** - press and hold a card, drag it to any column/position; hold without moving to get **Rename / Replan / Assign / Priority / Move to / Delete**.
* **Activity log** - every rename, move, replan, delete, column, member and team change is recorded on the server, shown per board and per issue, pushed live.
* **Multi-user** - Viewer / Member / Admin per board, directly or through **teams**; enforced on the server for every request.
* **Settings** - theme Light / Dark / Automatic, editable **nickname** (the *username* is immutable), **change password** (current password required, live ✓/✗ checklist of the rules), log out.
* **Error handling** - one `AppException` type, friendly messages, automatic crash/error reports uploaded to the server for developers (`GET /diagnostics/errors`).
* **Offline-first** - boards are cached; task changes made offline are queued and replayed in order.

## Run it

### Server
```bash
cd server_app
docker compose up --build            # Postgres + server on :8080
curl http://localhost:8080/health
```
Environment variables: `APP_ENV` (`production` refuses the built-in dev secret), `JWT_SECRET`, `JDBC_DATABASE_URL`, `DB_USER`, `DB_PASS`, `PORT`, `ACCESS_TOKEN_MS`, `REFRESH_TOKEN_MS`, `DEV_API_KEY` (enables the developer error list).
Existing databases are upgraded automatically on start (new columns/tables are added, old boards get their owner as ADMIN member, old issues get numbers).

### Android
Open `android_app/` in Android Studio. The server address is a **build-time** value:
```bash
./gradlew assembleDebug -PserverUrl=http://192.168.1.2:8080/   # physical device on your LAN
# default (emulator): http://10.0.2.2:8080/
```
Cleartext HTTP is allowed in **debug builds only**; release builds require `https://`.

## Tests
```bash
cd server_app  && ./gradlew test      # unit + end-to-end tests (H2 in memory)
cd android_app && ./gradlew test      # unit tests
```
See [docs/TESTING.md](docs/TESTING.md) for exactly what was and was not executed when this version was produced.

## Roles
| | Viewer | Member | Admin |
|---|:-:|:-:|:-:|
| See board, issues, history | ✓ | ✓ | ✓ |
| Create / edit / move / delete issues, comment | | ✓ | ✓ |
| Columns, WIP limits, board settings, people & teams, empty/delete board | | | ✓ |

Docs: [Architecture](docs/ARCHITECTURE.md) · [API](docs/API.md) · [Design system](docs/DESIGN.md) · [Testing](docs/TESTING.md) · [Changes](docs/CHANGES.md)
