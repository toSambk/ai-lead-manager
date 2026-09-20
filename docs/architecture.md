# Project architecture

AI Lead Manager is a modular monolith. Gradle builds the Kotlin modules into one Spring Boot service. The frontend is a separate npm project in the same repository.

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
- **backend:telegram:** Incoming Telegram updates and outgoing messages.
- **backend:ai:** Structured lead analysis through an AI provider and a local stub.
- **backend:app:** Spring Boot entry point, REST API, authorization, and module configuration.
- **frontend:** Customer and manager views for the Mini App. It accesses data only through the backend API.

The first PostgreSQL schema migration, datasource wiring, and repository adapters are implemented. The [preliminary API](api.md) has controller routes that return `501 Not Implemented`; lead use cases, Telegram integration, and AI processing will be added as the phases in the root README progress.
