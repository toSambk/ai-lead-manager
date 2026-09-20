# Persistence module

This module contains the Liquibase changelog, first PostgreSQL migration, Spring Data JDBC records and repositories, and adapters for the storage interfaces in `backend/core`. Spring Boot in `backend/app` provides the datasource, enables repository scanning, and runs Liquibase at startup. Basic writes and queries use Spring Data JDBC; custom SQL can be added here when a workflow needs it. See the [data model and persistence boundary](../../docs/data-model.md).
