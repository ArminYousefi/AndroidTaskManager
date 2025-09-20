
# Ktor Backend for MyTaskManager (sample)

## Run locally with Docker Compose
1. Build the fat jar:
   ```bash
   ./gradlew shadowJar
   ```
2. Build and run with docker-compose:
   ```bash
   docker compose up --build
   ```
3. Server will be available at http://localhost:8080

## Endpoints
- POST /auth/signup  { username, password, email? }
- POST /auth/login   { username, password }
- GET  /            health
