# Relata

Relata is an AI-assisted database relationship modeling and associated data query workspace.

The initial stack is:

- Backend: JDK 17, Spring Boot 3, H2, JDBC, Flyway
- AI: Spring AI Alibaba, prepared for DashScope integration
- Frontend: Vue 3, TypeScript, Vite, Element Plus, AntV X6
- Deployment: frontend static assets are served by the Spring Boot backend

## Project Structure

```text
.
├── backend/          Spring Boot application
├── frontend/         Vue 3 application
└── pom.xml           Maven parent project
```

## Requirements

- JDK 17+
- Maven 3.9+
- Node.js 20+
- npm 10+

## Run In Development

Backend API:

```bash
sh scripts/run-backend-only.sh
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend dev server proxies `/api` to `http://localhost:8080`.
Open the development UI at:

```text
http://localhost:5173
```

To serve the built UI from the backend on port 8080 during local development:

```bash
sh scripts/run-full.sh
```

## Build One Deployable Backend

```bash
sh scripts/build-all.sh
java -jar backend/target/relata-backend-0.1.0-SNAPSHOT.jar
```

The backend Maven build copies `frontend/dist` into the Spring Boot jar under `static/`.

Open:

```text
http://localhost:8080
```

## Docker

```bash
docker build -t relata:dev .
docker run --rm -p 8080:8080 relata:dev
```

H2 console:

```text
http://localhost:8080/h2-console
```

JDBC URL:

```text
jdbc:h2:file:./data/relata
```

## Spring AI Alibaba

The backend is prepared for Spring AI Alibaba DashScope usage. Configure it with environment variables or application properties when the API key is available.

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:}
```
