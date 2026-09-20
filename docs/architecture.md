# Project architecture

AI Lead Manager is a modular monolith. Gradle builds the Kotlin modules into one Spring Boot service. The frontend is a separate npm project in the same repository.

For local container runs, `infra/compose.yaml` builds that one backend service from `backend/app/Dockerfile` and starts it alongside PostgreSQL. The frontend still runs through Vite on the host, which proxies `/api` to the backend's localhost port. No Kotlin module runs as a separate container.

## Dependencies

~~~text
frontend  --HTTP-->  backend:app
                     ├── backend:core
                     ├── backend:persistence ──> backend:core
                     ├── backend:telegram ────> backend:core
                     └── backend:ai ──────────> backend:core
~~~

- **backend:core:** Entities, business rules, use cases, and interfaces for external dependencies. It does not know about Spring, Telegram, SQL, or AI APIs.
- **backend:persistence:** Liquibase migrations and Spring Data JDBC repository adapters for the foundation tables. See the [data model and module boundary](data-model.md).
- **backend:telegram:** Mini App `initData` verification; incoming bot updates and outgoing messages remain planned.
- **backend:ai:** Structured lead analysis through an AI provider and a local stub.
- **backend:app:** Spring Boot entry point, REST API, authorization, and module configuration.
- **frontend:** Customer and manager views for the Mini App. It accesses data only through the backend API.

The foundation and JDBC session migrations, datasource wiring, and repository adapters are implemented. The create-lead use case is in `backend/core`, its Spring configuration is in `backend/app/config`, and its HTTP DTOs are in `backend/app/dto`. `backend/telegram` verifies signed Mini App data; `backend/app/security` creates JDBC-backed sessions and reloads the user's current role from PostgreSQL for each request. `GET /api/categories` reads active categories through the core repository interface. `POST /api/leads` receives the authenticated principal through Spring Security. The frontend submits the customer form through that session. The remaining planned routes in the [preliminary API](api.md) return `501 Not Implemented`. Bot commands, outbound Telegram messages, and AI processing remain planned.
