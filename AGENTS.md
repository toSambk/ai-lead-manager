# Project instructions

## Repository

- GitHub remote: https://github.com/toSambk/ai-lead-manager
- Use main as the primary branch.

## Source of truth

- Read the root README.md for product scope, acceptance criteria, and roadmap.
- Read docs/architecture.md before changing module boundaries.
- Keep documentation aligned with implemented behavior. Label planned features clearly.

## Language

- Write documentation, code comments, commit messages, and UI copy in English.
- Use clear domain names such as Lead, Customer, Manager, and LeadStatus.

## Architecture

- This is one repository and one deployable Spring Boot backend.
- Keep business rules and external dependency interfaces in backend/core. The core module must not depend on Spring, PostgreSQL, Telegram, or an AI SDK.
- Put PostgreSQL code and migrations in backend/persistence, bot integration in backend/telegram, AI integration in backend/ai, and REST endpoints and module wiring in backend/app.
- Keep frontend as a separate React/TypeScript npm project. It communicates with the backend through HTTP APIs.
- Add infrastructure only when a current feature needs it; preserve the modular monolith.

## Local commands

- From the repository root, build and test the backend with .\gradlew.bat build. Use the project Gradle Wrapper rather than a system Gradle installation.
- From frontend, run npm.cmd run build and npm.cmd run lint after frontend changes. On Windows PowerShell, use npm.cmd if npm.ps1 is blocked by execution policy.
- Run only checks relevant to the changed modules. Report checks that could not run.

## Security and reliability

- Never commit tokens, API keys, or real customer data. Use environment variables and keep .env out of version control.
- Verify Telegram Mini App initData on the backend before trusting user identity or role.
- Make Telegram update handling idempotent.
- AI output is untrusted input: validate its structure and preserve an explicit failure state. Customer-facing AI suggestions require manager approval before sending.
