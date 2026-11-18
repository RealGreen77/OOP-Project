# I-ITSM — Intelligent IT Service Management & Incident Resolution System

A full-stack platform that manages the complete lifecycle of IT incidents —
**Report → Classify → Prioritise → Assign → Investigate → Resolve → Verify → Close** —
with automatic prioritisation, SLA monitoring, smart technician assignment, duplicate
detection, incident correlation, automatic escalation, real-time notifications and a
complete audit trail.

Built for **CS F213 Object-Oriented Programming (BITS Pilani, Sem I 2026-27)**. The design
deliberately showcases object-oriented principles and design patterns. Each concept is
tagged in the source with comments such as `// [OOP: Polymorphism]` or `// [Pattern: State]`,
so you can find it with grep.

| | |
|---|---|
| **Backend** | Java 21 · Spring Boot 3.5 · Spring Security (JWT) · Spring Data JPA / Hibernate · Flyway · WebSocket (STOMP) |
| **Frontend** | React 19 · TypeScript · Vite · TanStack Query · Tailwind CSS 4 · Recharts |
| **Data** | PostgreSQL 17 (H2 in PostgreSQL mode for tests / local demo) |
| **Ops** | Docker Compose · nginx · Mailpit (SMTP catcher) · GitHub Actions CI · OpenAPI/Swagger · Actuator |
| **Quality** | 70 backend tests (unit + API integration, ~79% instruction coverage) · 9 frontend tests |

---

## Quick start

### Option A — Docker (recommended, production-like)

```bash
docker compose up --build
```

| URL | What |
|---|---|
| http://localhost:3000 | Web application |
| http://localhost:8080/swagger-ui.html | Interactive API documentation |
| http://localhost:8025 | Mailpit inbox — every e-mail notification lands here |

### Option B — Local without Docker (Java 21 + Node 20+)

```bash
# terminal 1 - backend on :8080 with a file-based H2 database (seeded automatically)
cd backend && ./mvnw spring-boot:test-run

# terminal 2 - frontend on :5173 (proxies /api and /ws to the backend)
cd frontend && npm install && npm run dev
```

To run the backend against your own PostgreSQL instead, use `./mvnw spring-boot:run` with
`DB_URL`, `DB_USERNAME` and `DB_PASSWORD` set.

### Demo accounts (password `Password@123`)

The login page has one-click buttons for each of these.

| Role | E-mail | Notes |
|---|---|---|
| Employee | `employee@itsm.local` | Asha Rao — has an incident waiting for verification |
| Technician | `tech@itsm.local` | Arjun Mehta — Network & Cloud, SMS alerts on |
| Team Lead | `lead@itsm.local` | Vikram Rao — Applications team, first escalation level |
| Administrator | `admin@itsm.local` | Priya Sharma — full configuration access |
| IT Manager | `manager@itsm.local` | Rajesh Iyer — final escalation level |

More seeded users: technicians `sneha.patel`, `rahul.verma`, `kavya.nair`, `rohan.das`,
`meera.joshi`; team lead `lead.infra`; employees `karan.singh`, `divya.menon`,
`nikhil.kumar`, `isha.agarwal`. All of them are `@itsm.local`.

### Accounts and passwords

- **Register:** new employees enter their details (the password is typed twice), receive a 6-digit
  code by e-mail and enter it to create the account. With Docker and no `.env`, the code arrives in
  Mailpit (http://localhost:8025). For `@itsm.local` addresses it is printed in the backend log
  (`docker compose logs backend | grep VERIFICATION`).
- **Forgot password:** *Sign in → Forgot password?* e-mails a single-use link valid for 30 minutes.
- **Show password:** every password field has an eye button.
- Seeded demo accounts and accounts created by an administrator do not need e-mail verification.

---

## Demo script — the three required workflows

**Workflow 1 — Employee reports → system prioritises → technician assigned → notification generated**
1. Log in as **Employee** and choose **Report incident**. Pick *Payment Gateway*, enter the title
   "Checkout page throws error" and set Impact *High* and Urgency *Critical*. Watch the
   **Live analysis** panel: it suggests a category, shows the predicted priority (P1) with its SLA
   targets, and lists similar open incidents.
2. Submit. The incident is automatically:
   - classified as **Application**;
   - prioritised as **P1** (Impact 3 × Urgency 4 × Criticality 4 = 48);
   - given the stricter **Payment Gateway P1** SLA (5 min response, 20 min resolution);
   - assigned by the **Smart** strategy.
3. The **Audit history** tab shows every step. The technician receives an instant in-app toast,
   an e-mail (visible in Mailpit) and, for P1, a simulated SMS (Admin → Delivery log).

**Workflow 2 — Technician investigates → SLA monitored → incident resolved → user notified**
1. Log in as the assigned technician (**My queue**). Click *Acknowledge*: the SLA response time
   is recorded. Then click *Start investigation*.
2. Click *Request info*. The status becomes PENDING_INFO and **the SLA clock pauses**. As the
   employee, click *Provide information* and the clock resumes with its deadline pushed back.
3. The technician opens **Suggested resolutions** and clicks *Use this resolution*, which
   pre-fills the form, then clicks *Resolve*. The employee is notified, confirms the fix and
   rates it. The technician closes the incident. The SLA panel shows **Met** or **Breached**.

**Workflow 3 — Multiple incidents → duplicates/related identified → correlated/escalated**
1. As three different employees, report "Payment application unavailable",
   "Payment transaction failing" and "Unable to make payment".
2. From the 2nd report onward, each incident is flagged **Possible duplicate of INC…**
   (TF-IDF similarity plus a domain synonym map). The assignee is notified.
3. **Intelligence** (Admin / Team Lead) shows a **major-incident cluster** with its probable root
   service, and administrators get a *Major incident* alert.
4. Open a duplicate and click *Merge into INC…*, which closes it as a duplicate. Unacknowledged
   P1/P2 incidents escalate automatically: Team Lead → Administrator → IT Manager.
   *SLA policies → Run SLA scan now* triggers the background monitor immediately.

---

## Feature map vs. the project brief

| Brief requirement | Implementation |
|---|---|
| Users: Employee, Technician, Administrator | Plus Team Lead and IT Manager (`User` hierarchy, 5 concrete roles) |
| Incident CRUD + lifecycle | `Incident` aggregate + **State pattern** (9 states, illegal transitions impossible) |
| Priority (impact, urgency, criticality) | **2 strategies**: Matrix (I×U×C) and Weighted (+ affected users, + historical recurrence) |
| ≥ 2 assignment strategies | **4**: Skill-based, Load-balanced, Round-robin, Smart scoring. Switchable at runtime |
| SLA calculation / tracking / violation | Per-service overrides, pause on PENDING_INFO, warnings at 75%, background monitor |
| ≥ 2 notification mechanisms | **3**: In-app (real-time WebSocket), E-mail (SMTP adapter), SMS (adapter over a vendor-style client) |
| Complete audit history | Immutable `AuditEntry`, written by an Observer in the same transaction |
| ≥ 5 search criteria | **8+**: text/number, date range, status, priority, category, service, technician, SLA status (+ escalated, unassigned) and CSV export |
| ≥ 2 intelligent features | **All 7 (A–G)**: duplicate detection, correlation, automatic escalation, smart recommendation, SLA risk prediction, auto-categorisation, suggested resolution |
| Persistence | PostgreSQL + Flyway migrations, Hibernate `validate` |
| Security | BCrypt, JWT, role and permission authorization, object-level access policy, lockout after 5 failed logins, e-mail verification (one-time code) at registration, forgot-password via single-use e-mailed link, password confirmation and show/hide toggle, input validation, RFC 7807 errors |
| Innovation | Real-time notifications and live dashboard, analytics charts, predictive SLA, similarity detection, service dependency graph, automated escalation, Docker, CI |

## Where to find each OOP concept

| Concept | Where (examples) |
|---|---|
| **Encapsulation** | `Incident` — no public status setter; state changes only via lifecycle methods. `User` password hash is never exposed. `AuditEntry` has no setters at all |
| **Abstraction** | `User`, `IncidentState`, `EscalationHandler`, `AbstractScoringAssignmentStrategy`, `ItsmException`, `BaseEntity` |
| **Inheritance** (true "is-a") | `User` → `Employee` / `Technician` → `TeamLead` / `Administrator` → `ITManager`. `IncidentState` → `ActiveWorkState` → `AssignedState`… |
| **Polymorphism** | `user.permissions()`, `incident.currentState().resolve(...)`, `strategy.assess(...)`, `channel.deliver(...)`, `exception.type()` |
| **Interfaces** | `PriorityStrategy`, `AssignmentStrategy`, `NotificationChannel`, `EmailGateway`, `SmsGateway`, `CategorySuggester`, `IncidentEventListener`, `RatedLevel` |
| **Composition** | `Incident` owns `SlaRecord`, `Resolution`, `Feedback` and its `IncidentNote`s |
| **Association / Aggregation** | Incident ↔ reporter/assignee/service/category. `TeamLead` aggregates `Technician`s. Service dependency graph |
| **Exception handling** | 16-class `ItsmException` hierarchy → one `GlobalExceptionHandler` → RFC 7807 responses |
| **Collections** | `EnumMap` (state and channel registries), `EnumSet` (permissions), `PriorityQueue` (ranking), `ArrayDeque` (graph BFS), `ConcurrentHashMap` + `AtomicInteger` (round-robin), `TreeMap` (trend buckets) |
| **Generics** | `StrategyRegistry<T extends NamedStrategy>`, `PageResponse<T>` |

## Design patterns (8)

| Pattern | Used for | Key types |
|---|---|---|
| **State** | Incident lifecycle | `IncidentState`, `NewState` … `ClosedState`, `IncidentStateFactory` |
| **Strategy** | Priority calculation, technician assignment | `PriorityStrategy`, `AssignmentStrategy`, `StrategyRegistry` |
| **Observer** | Audit, notifications, live updates | `IncidentEventPublisher`, `IncidentEventListener`, `InProcessEventBus` |
| **Factory** | Users, incidents, notification channels, states | `UserFactory`, `IncidentFactory`, `NotificationChannelFactory`, `IncidentStateFactory` |
| **Adapter** | External e-mail and SMS providers | `SmtpEmailGateway`, `SimulatedSmsGatewayAdapter` → `ThirdPartySmsClient` |
| **Chain of Responsibility** | Escalation Team Lead → Admin → IT Manager | `EscalationHandler` and subclasses, `EscalationChain` |
| **Template Method** | Shared assignment algorithm skeleton | `AbstractScoringAssignmentStrategy.rank()` |
| **Repository / Builder** | Persistence, search criteria, audit entries | Spring Data repositories, `IncidentSearchCriteria.Builder`, `AuditEntry.Builder` |

## Project structure

```
backend/                     Spring Boot REST API
  src/main/java/com/itsm/
    domain/                  entities & value objects (user, incident + states, catalog, sla, audit, notification)
    engine/                  algorithms: priority/, assignment/, escalation/, intelligence/, sla/
    events/                  Observer: event hierarchy, bus, audit/notification/realtime listeners
    notification/            channels, adapters, factory, dispatcher, WebSocket publisher
    service/                 application services (use cases) + access policy + SLA monitor
    repository/              Spring Data repositories + search specifications
    security/                JWT, principal, current user
    web/                     REST controllers + DTOs + mapper
    exception/               exception hierarchy + global handler
  src/main/resources/db/migration   Flyway SQL schema
  src/test/java              unit tests (domain, engines) + API integration tests
frontend/                    React SPA (features/, components/, api/, auth/, hooks/)
docs/                        requirements, design (UML), technology report, test cases
docker-compose.yml           PostgreSQL + Mailpit + backend + frontend
```

## Running the tests

```bash
cd backend && ./mvnw verify      # 70 tests + JaCoCo report at target/site/jacoco/index.html
cd frontend && npm test          # Vitest
```

## Documentation

- [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) — problem analysis, stakeholders, requirements, business rules
- [docs/DESIGN.md](docs/DESIGN.md) — architecture, UML (use case, class, state, sequence), ER diagram, algorithms
- [docs/TECHNOLOGY.md](docs/TECHNOLOGY.md) — technology selection report (the 9 questions)
- [docs/TEST_CASES.md](docs/TEST_CASES.md) — documented test cases mapped to automated tests

## Sending real e-mail (optional)

By default every e-mail goes to the Mailpit catcher at http://localhost:8025. To deliver to real inboxes,
copy `.env.example` to `.env`, uncomment the `SMTP_*` / `MAIL_FROM` lines and fill in a provider
(for Gmail use an **app password**, not your normal password), then run `docker compose up -d`.
Users receive e-mail at the address on their account, so register with a real address to test.
Set `SIMULATED_EMAIL_DOMAINS=itsm.local` (already in `.env.example`) so the seeded demo accounts are not e-mailed
(their delivery shows as SIMULATED in the delivery log) and only real addresses receive mail.
