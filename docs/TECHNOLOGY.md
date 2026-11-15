# I-ITSM — Technology Selection Report

## Stack summary

| Layer | Technology | Version |
|---|---|---|
| Language (backend) | Java | 21 (LTS) |
| Backend framework | Spring Boot (Web, Security, Data JPA, Validation, WebSocket, Mail, Actuator) | 3.5 |
| Persistence | PostgreSQL · Hibernate ORM · Flyway migrations · H2 (tests / local) | 17 · 6.6 · 11 |
| Auth | JWT (jjwt) · BCrypt | 0.12 |
| API docs | springdoc-openapi (Swagger UI) | 2.8 |
| Language (frontend) | TypeScript | 5.9 |
| Frontend | React · React Router · TanStack Query · Tailwind CSS · Recharts · STOMP.js | 19 · 7 · 5 · 4 · 3 · 7 |
| Build | Maven (wrapper) · Vite | 3.9 · 7 |
| Testing | JUnit 5 · Mockito · Spring MockMvc · AssertJ · JaCoCo · Vitest · Testing Library | — |
| Ops | Docker · Docker Compose · nginx · Mailpit · GitHub Actions | — |

## 1. Why this programming language?

**Java 21** was chosen for the backend because:
- **It is the language of CS F213**, so evaluators and every team member can read the OOP
  design directly.
- **It enforces OOP constructs** that Python and JavaScript only imitate: real interfaces,
  abstract classes, access modifiers (`private` / package-private / `protected`), checked type
  hierarchies, and final classes.
- **Modern Java features are used deliberately:**
  - *Records* for immutable DTOs and value objects.
  - *Sealed classes* (`IncidentEvent`) with exhaustive pattern-matching `switch`, so the
    compiler guarantees every event type is handled.
  - Enums with behaviour (`RatedLevel`).
  - *Virtual threads* for scalable request handling.
- **It is strongly typed.** Refactoring a domain model with 60+ classes is safe.

**TypeScript** was chosen for the frontend: static typing mirrors the backend DTOs
(`types.ts`), so API contract mistakes are caught at compile time.

## 2. Why this frontend technology?

**React 19 + Vite**:
- **Component model** fits a UI with three distinct role experiences that share building blocks
  (badges, tables, dialogs).
- **TanStack Query** gives server-state caching, background refetching and simple invalidation
  when WebSocket events arrive. This is what makes the dashboards live.
- **Tailwind CSS** builds a consistent design system (tokens, dark mode) quickly. **Recharts**
  provides the declarative charts.
- **Vite** gives instant dev reloads and an optimised, code-split production bundle.

## 3. Why this backend technology?

**Spring Boot 3.5**:
- **Dependency injection is what makes the Strategy, Observer and Factory patterns cheap.** Every
  `PriorityStrategy`, `AssignmentStrategy`, `NotificationChannel` and `IncidentEventListener`
  bean is discovered automatically and injected as a `List<T>` into a registry, bus or factory.
  Adding an implementation requires zero changes elsewhere (Open/Closed principle).
- **Spring Security** gives a production-grade filter chain for JWT and method-level
  `@PreAuthorize`. **Spring Data JPA** implements the Repository pattern.
- **Built-in scheduling** (SLA monitor), **WebSocket/STOMP** (real-time notifications),
  **Mail** (SMTP adapter) and **Actuator** (health and metrics) cover the brief's needs without
  extra infrastructure.
- **Maturity:** Spring Boot 3.5 is a mature, maintained line. Its APIs are stable and well
  documented, which lowered delivery risk under a one-week deadline.

## 4. Why this database?

**PostgreSQL 17**:
- **Relational integrity fits the domain.** Incidents reference users, services, categories and
  each other (duplicates). Foreign keys, unique constraints and CHECK constraints enforce
  invariants at the storage level too.
- **Reporting is aggregate-heavy** (group-by status, priority, service; SLA compliance), which
  SQL handles natively.
- **Transactions** guarantee that an incident change and its audit entry commit together.
- **It is mature and free**, with first-class support in Spring, Flyway and Docker.
- **Flyway** versions the schema; Hibernate only *validates* it.
- **H2 in PostgreSQL mode** runs the *same* migrations in tests and in the no-Docker local mode.

## 5. Why this architecture?

**A layered monolith** (presentation → services → domain/engines → repositories) **with an
event-driven core**:
- **Separation of concerns:** controllers are thin, services orchestrate use cases, and the
  domain enforces invariants (State pattern, value objects). Engines hold pure, unit-testable
  algorithms.
- **Events decouple side effects:** auditing, notifications and the live feed are observers. The
  incident workflow does not know they exist.
- **Right-sized for the scope:** a single deployable keeps transactions simple (incident plus
  audit are atomic) and is easy to run in a demo. The event bus is an interface, which leaves a
  clean path to microservices (see question 9).
- **The REST API, documented with OpenAPI, makes the backend client-agnostic.** A mobile app
  could reuse it unchanged.

## 6. What alternatives did you consider?

| Area | Alternatives | Why not chosen |
|---|---|---|
| Backend | NestJS (TypeScript), Django/FastAPI (Python), ASP.NET Core | NestJS and ASP.NET are viable OOP stacks, but the course is taught in Java. Python's OOP lacks enforced encapsulation and interfaces, which weakens the OOP demonstration |
| Frontend | Angular, Vue, server-rendered Thymeleaf | Angular is heavier for a small team. Thymeleaf cannot deliver the real-time, highly interactive UI |
| Database | MongoDB, MySQL | The data is highly relational with integrity rules, so a document store is a poor fit. MySQL would work; PostgreSQL has richer SQL and constraint support |
| Real-time | Polling, Server-Sent Events | Polling wastes resources and adds latency. SSE is one-way; STOMP over WebSocket supports per-user queues and topics in Spring natively |
| Architecture | Microservices from day one | Distributed transactions and operations overhead are not justified at this scale. The design is ready to split later |
| Intelligence | LLM-based classification | Deferred: API-key availability was uncertain. The `CategorySuggester` interface is in place for an AI adapter |

## 7. Advantages and limitations of the stack

**Advantages**
- Strong typing end to end (Java + TypeScript).
- Mature security and persistence.
- Patterns map naturally onto dependency injection.
- Real-time UX.
- One-command deployment.
- Excellent tooling (Swagger, Actuator, JaCoCo, CI).

**Limitations**
- **JVM start-up time and memory footprint** are higher than Go or Node. This is acceptable for
  a long-running service and mitigated with virtual threads and container memory limits.
- **The in-memory STOMP broker and in-process event bus are single-instance.** Scaling out
  requires an external broker (see question 9).
- **The SMS gateway is simulated.** E-mail is real SMTP but uses Mailpit in the demo.
- **Keyword and TF-IDF intelligence is transparent but less nuanced than ML.** An ML or LLM model
  can be plugged in behind the existing interfaces.

## 8. How does the technology support object-oriented design?

- **Java language features:** interfaces, abstract classes, `final`, and access control give
  encapsulation and abstraction. Sealed hierarchies with pattern matching give safe polymorphism.
- **JPA inheritance** (`SINGLE_TABLE`) persists a true class hierarchy:
  `User → Employee | Technician → TeamLead | Administrator → ITManager`. Polymorphic queries
  return subclasses.
- **JPA `@Embeddable`** models composition (`SlaRecord`, `Resolution`, `Feedback` live inside
  `Incident`). Associations and aggregation map to `@ManyToOne` / `@ManyToMany`.
- **Spring DI** turns the Strategy, Observer and Factory patterns into configuration-free
  extension points. The **Repository** pattern comes from Spring Data.
- **TypeScript** gives the frontend typed models of the same domain concepts.

## 9. How would the system evolve if users increased significantly?

1. **Horizontal scaling of the API.** It is already stateless (JWT), so add instances behind a
   load balancer.
2. **Real-time at scale.** Switch Spring's simple broker to a STOMP broker relay
   (RabbitMQ/ActiveMQ) so WebSocket messages reach users on any instance.
3. **Asynchronous side effects.** Implement `IncidentEventPublisher` with Kafka or RabbitMQ.
   Notification and analytics consumers then scale independently. Use an outbox table to keep
   auditing transactional. No publisher code changes are needed.
4. **Database.**
   - Read replicas for dashboards and search.
   - Partitioning `audit_entries` and `notifications` by month.
   - Connection pooling (PgBouncer).
   - A cache (Redis) for catalog data and computed statistics, which already have a cache seam.
5. **Search.** Move free-text and similarity search to OpenSearch/Elasticsearch, or embeddings
   with pgvector, for large histories.
6. **Service extraction.** Split along existing package boundaries (notifications, intelligence,
   reporting) into microservices when team or scale demands. Package boundaries already align
   with these services.
7. **Operations.** Kubernetes with autoscaling, centralised logging and metrics (Prometheus via
   Actuator), and blue/green deployments from the existing CI pipeline.
