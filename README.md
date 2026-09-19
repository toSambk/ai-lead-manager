# AI Lead Manager

A portfolio project modeled after a small freelance delivery: a Telegram bot and Mini App for receiving and managing service inquiries. A customer submits a request for website development or automation, AI prepares a structured summary, and a manager handles the request through completion.

This document defines the target requirements, the scope of the first release, and the development roadmap. Features described below are **planned unless explicitly listed as implemented**.

## Current implementation

The monorepo skeleton is in place: five Kotlin Gradle modules, a React/TypeScript frontend, and a PostgreSQL Compose configuration. The implemented behavior is limited to a Spring Boot application, GET /api/system, an initial lead status type, and a frontend screen that checks backend connectivity. Lead intake, Telegram integration, persistence, and AI are still planned.

~~~text
ai-lead-manager/
├── backend/
│   ├── app/          # Spring Boot entry point, REST API, module wiring
│   ├── core/         # Lead model and business rules
│   ├── persistence/  # PostgreSQL and migrations (planned)
│   ├── telegram/     # Bot integration (planned)
│   └── ai/           # Lead analysis (planned)
├── frontend/         # React + TypeScript, separate npm project
├── infra/            # Docker Compose
├── docs/             # Architecture notes
├── gradle/           # Gradle Wrapper
├── settings.gradle.kts
└── README.md
~~~

The backend modules produce one Spring Boot service. The core module has no integration dependencies; persistence, telegram, and ai depend on core; app wires them together. The frontend lives in the same repository and is built with npm. See [module architecture](docs/architecture.md).

### Run the current skeleton

Install JDK 21 and a Node.js LTS release. A system Gradle installation is unnecessary because the repository includes a pinned Gradle Wrapper.

~~~powershell
# Terminal 1, from the repository root
.\gradlew.bat :backend:app:bootRun

# Terminal 2
cd frontend
npm.cmd install
npm.cmd run dev
~~~

Open the URL printed by Vite, usually http://localhost:5173. When the backend is running, the frontend displays “Backend available”. The sample API is at http://localhost:8080/api/system; Spring Boot health is at http://localhost:8080/actuator/health.

PostgreSQL is not connected to the application yet. When local storage is needed, copy .env.example to .env, set POSTGRES_PASSWORD in .env, and run this command from the repository root:

~~~powershell
docker compose --env-file .env -f infra/compose.yaml up -d
~~~

This command requires a working Docker Desktop installation. The current backend and frontend can run without PostgreSQL.

## 1. Goal and primary workflow

A small agency receives inquiries through Telegram. Today a manager collects requirements, asks follow-up questions, and copies information into a working list. AI Lead Manager should keep the full inquiry workflow in one place and reduce manual preparation of lead records.

1. A customer opens the bot and selects “Submit a request”.
2. In the Mini App, the customer provides a service category, task description, budget, deadline, and contact details.
3. The backend saves the lead and assigns a reference number; the bot confirms receipt.
4. AI summarizes the request, extracts known facts, identifies missing information, and suggests a follow-up question and priority.
5. If clarification is needed, the bot asks the customer a question and stores the reply with the same lead.
6. A manager receives a notification, opens the lead in the Mini App, assigns an owner, changes its status, and replies through the bot.

## 2. Roles

| Role | Permissions |
| --- | --- |
| Customer | Create a lead, answer follow-up questions, and view the status of their own leads. |
| Manager | View agency leads, filter and open records, assign owners, change status, add internal notes, and send replies to customers. |
| Administrator | All manager permissions, plus employee access, service categories, and AI processing settings. |

The backend enforces permissions. A user cannot grant themselves a role by sending modified Mini App data.

## 3. Functional requirements

### Telegram bot

- Handle /start and /help and provide a button that opens the Mini App.
- Confirm lead creation and provide the lead reference number.
- Accept a customer's reply to a follow-up question and associate it with the active lead.
- Notify the assigned manager or team chat about new leads and important changes.
- Deliver a message to a customer only after a manager confirms it.

### Customer Mini App

- Provide a form with service category, task description, estimated budget, desired deadline, and contact details.
- Validate required fields and display clear errors.
- Show a success screen with the lead reference number.
- List the customer's own leads and current statuses.
- Work well on a phone screen.

### Manager Mini App

- List leads with search and filters for status, priority, and owner.
- Show the original customer data, message history, AI summary, missing information, and the reason for the suggested priority.
- Allow status changes, owner assignment, and internal notes.
- Let a manager draft and confirm a reply before the bot sends it.
- Show an audit trail of who changed what and when.

### AI processing

- Extract the service category, task, budget, and deadline when these facts are present in the customer's text.
- Return a concise summary, missing fields, a suggested follow-up question, and a priority with an explanation.
- Return a defined data structure; the backend validates the response before saving it.
- Leave unknown fields empty instead of inventing values.
- Never send a proposal or manager reply to a customer without human approval.
- Leave the lead available for manual processing when AI fails or is unavailable.
- Provide a local stub mode that does not require a paid AI API.

### Lead lifecycle

Statuses: NEW → CLARIFICATION → IN_PROGRESS → COMPLETED / REJECTED.

Record transitions and ownership changes in an audit log. Repeated delivery of a Telegram update must not create duplicate leads or messages.

## 4. Data and technical design

Planned stack:

- **Backend:** Kotlin, Spring Boot, Gradle, REST API, and Telegram Bot API integration.
- **Frontend:** React and TypeScript in one Mini App with customer and manager views.
- **Database:** PostgreSQL with versioned schema migrations.
- **Local environment:** Docker Compose for PostgreSQL and later application services.
- **AI:** An isolated provider interface and a local stub implementation.

The first release uses one backend service with the app, core, persistence, telegram, and ai modules. Planned primary data objects are users, leads, lead messages, lead events, AI results, and AI jobs. The exact schema and API contracts will be refined before each implementation phase and recorded in migrations and API documentation.

### Access and reliability

- Verify Telegram initData and its age on the backend before granting Mini App access. Do not treat initDataUnsafe as authenticated identity.
- Read the bot token and AI provider key from environment variables; do not commit or log secrets.
- Verify the Telegram webhook secret and process updates idempotently.
- Run AI work separately from lead storage, with bounded retries and a visible failure state.
- Log failures with a lead identifier without publishing sensitive customer content.

## 5. Acceptance criteria for the first release

1. A customer creates a lead in the Mini App and receives its reference number from the bot.
2. A manager sees the lead in the list and can open and process it.
3. AI summarizes the lead and marks unknown facts as missing without inventing values.
4. A reply to a follow-up question is saved on the original lead.
5. Processing the same Telegram update twice does not create a duplicate.
6. A manager can still see a lead and its AI state when the AI provider is unavailable.
7. A customer cannot open another customer's lead; a manager cannot perform administrator actions.
8. The lead record shows status and owner changes in its audit history.
9. The project starts from its README with sample data and an AI stub mode.
10. The primary workflow is checked on a phone in Telegram and covered by relevant API and business logic tests.

## 6. First-release scope

The first release supports **one business and one inbound channel: Telegram**. Payments, WhatsApp, voice transcription, external CRM integration, advanced analytics, and autonomous AI proposal delivery are outside this release.

## 7. Roadmap

For solo development, plan roughly **8–12 weeks at 10–15 hours per week**. This is an estimate that depends on prior Kotlin, Spring Boot, and frontend experience.

| Phase | Work | Demonstrable result |
| --- | --- | --- |
| 1. Foundation | Define fields, statuses, and roles; create backend and frontend; start PostgreSQL; add migrations and local run instructions. | The API creates and retrieves a test lead. |
| 2. First complete flow | Create the bot, /start, Mini App button, customer form, and lead storage. | A lead submitted through Telegram is stored and the customer receives a reference number. |
| 3. Manager workspace | Add list and detail views, filters, status changes, owner assignment, notes, and notifications. | A manager handles a lead without direct database access. |
| 4. AI | Add fact extraction, summary, missing fields, clarification, priority, response validation, stub mode, and failure handling. | AI assists processing without blocking it when unavailable. |
| 5. Access and reliability | Validate initData, enforce roles, deduplicate Telegram events, queue AI jobs, add audit history and focused tests. | Repeated updates and service failures do not break the primary workflow. |
| 6. Release | Add HTTPS, webhook, deployment, phone verification, screenshots, demo, and final run instructions. | The project can be shown as a completed portfolio delivery. |

## 8. First development milestone

The first milestone is **a lead traveling from the Telegram Mini App to PostgreSQL and appearing in a manager view without AI**.

1. Confirm the form fields, statuses, and roles in this README. Prepare 3–5 realistic sample leads.
2. Add PostgreSQL connectivity and the first migration to the existing Kotlin/Spring Boot modules.
3. Implement API operations to create a lead, retrieve one lead, and list leads. Check them locally.
4. Build a simple Mini App form and connect it to the API.
5. Create a test bot with @BotFather, add /start, and provide the Mini App button.
6. Verify the full flow on a phone, then continue with the manager workspace and AI.

The first small repository result should include a running backend, a migration that creates the lead tables, POST /api/leads to store a lead, and GET /api/leads/{id} to retrieve it.

## 9. Development prerequisites

### Local tools

- JDK 21, IntelliJ IDEA, and Git.
- Node.js for frontend development.
- Docker Desktop with Docker Compose for PostgreSQL and reproducible local services.
- An HTTP client, such as the one integrated into the IDE, for testing API requests.

### Accounts and infrastructure

- A Telegram account and a test bot created with @BotFather.
- An AI provider key starting in phase 4; the local stub is used before then.
- A public HTTPS URL for end-to-end Mini App and webhook testing, followed by an application host.
- A source repository for code and demo materials.

Learn Kotlin/Spring Boot REST, SQL and migrations, React/TypeScript, HTTP authorization, Docker Compose, and API testing as each phase requires them.

## 10. Final deliverables

A working bot and Mini App, source code, database migrations, local setup instructions, test scenarios, screenshots, and a short demo of the full lead workflow.

## Reference documentation

- [Telegram Mini Apps](https://core.telegram.org/bots/webapps)
- [Telegram Bot API](https://core.telegram.org/bots/api)
- [Creating a bot with BotFather](https://core.telegram.org/bots/features#creating-a-new-bot)
- [Kotlin with Spring Boot](https://kotlinlang.org/docs/jvm-get-started-spring-boot.html)
- [Docker Compose](https://docs.docker.com/compose/)
