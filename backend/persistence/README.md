# Persistence module

This module contains Liquibase migrations for the application tables and Spring Session JDBC tables, Spring Data JDBC records and repositories, and adapters for the storage interfaces in `backend/core`. Telegram profile upsert uses PostgreSQL `ON CONFLICT` and preserves an existing user role. Spring Boot in `backend/app` provides the datasource, enables repository scanning, and runs Liquibase at startup. See the [data model and persistence boundary](../../docs/data-model.md).
