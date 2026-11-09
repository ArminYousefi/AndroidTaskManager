# Server

See the repository [README](../README.md), [docs/API.md](../docs/API.md) and [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

```bash
docker compose up --build        # server + Postgres
./gradlew run                    # needs a local Postgres, see env vars in the root README
./gradlew test                   # unit + end-to-end tests (H2)
curl localhost:8080/health
curl -H "X-Dev-Key: $DEV_API_KEY" localhost:8080/diagnostics/errors   # crash reports from the app
```
Package layout: `domain/` pure rules (shared with the app) · `models/` tables + DTOs · `services/` business logic + authorisation ·
`routes/` HTTP · `plugins/` error handling + DB init · `config/` environment.
