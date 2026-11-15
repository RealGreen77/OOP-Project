# I-ITSM — System Design

All diagrams are Mermaid and render on GitHub. For the PDF report, paste a diagram into
https://mermaid.live and export it as PNG or SVG.

## 1. Architecture

Layered architecture (the brief's "Choice 1"), with an event-driven core for side effects.

```mermaid
flowchart TB
  subgraph Client["Browser - React SPA (role-specific UI)"]
    UI[Pages & components] --> RQ[TanStack Query cache]
    UI --> WSClient[STOMP client]
  end
  subgraph Edge["nginx"]
    Static[Static assets] 
    Proxy[/api & /ws reverse proxy/]
  end
  subgraph API["Spring Boot backend"]
    direction TB
    Sec[Security filter: JWT -> UserPrincipal] --> Ctl[REST controllers + DTO validation]
    Ctl --> Svc[Application services<br/>IncidentService, AssignmentService, ...]
    Svc --> Policy[IncidentAccessPolicy]
    Svc --> Dom[Domain model<br/>Incident + State, User hierarchy, SlaRecord]
    Svc --> Eng[Engines<br/>Priority & Assignment strategies,<br/>Escalation chain, Intelligence]
    Svc --> Bus((Event bus<br/>Observer))
    Bus --> Audit[AuditTrailListener]
    Bus --> Notif[NotificationListener]
    Bus --> Live[RealtimeUpdateListener]
    Notif --> Disp[NotificationDispatcher] --> Chan[Channels: In-app / Email / SMS<br/>via Adapters]
    Sched[SLA monitor scheduler] --> Svc
    Svc --> Repo[Repositories - Spring Data JPA]
  end
  DB[(PostgreSQL<br/>Flyway schema)]
  SMTP[[SMTP - Mailpit]]
  SMS[[SMS vendor client - simulated]]
  RQ -- HTTPS JSON --> Proxy --> Ctl
  WSClient -- WebSocket --> Proxy
  Live -- /topic/incidents --> WSClient
  Chan -- /user/queue/notifications --> WSClient
  Chan --> SMTP
  Chan --> SMS
  Repo --> DB
```

**Request lifecycle — report an incident**

```mermaid
sequenceDiagram
  actor E as Employee
  participant C as IncidentController
  participant S as IncidentService
  participant F as IncidentFactory
  participant I as Incident (State)
  participant P as PriorityStrategy
  participant R as SlaPolicyResolver
  participant D as DuplicateDetector
  participant A as AssignmentStrategy
  participant B as EventBus
  participant L as Listeners (Audit / Notify / Live)
  E->>C: POST /api/incidents
  C->>S: report(request)
  S->>F: report(title, ..., reporter, service)
  F-->>S: Incident (NEW)
  S->>B: Created
  B->>L: audit + "Incident received" + live feed
  S->>I: classify(category)  [NEW -> CLASSIFIED]
  S->>P: assess(impact, urgency, criticality, users)
  P-->>S: P1 + score + rationale
  S->>R: resolve(service, P1)
  R-->>S: "Payment Gateway P1" policy
  S->>I: applyPriority(), applySlaPolicy()
  S->>D: findSimilar(open incidents)
  D-->>S: suspected duplicate (score)
  S->>A: select(incident, candidates with live workload)
  A-->>S: best technician + reasons
  S->>I: assignTo(technician) [CLASSIFIED -> ASSIGNED]
  S->>B: Classified, PriorityAssigned, SlaApplied, DuplicateSuspected, Assigned
  B->>L: audit trail, notify technician (in-app + email + SMS for P1), live refresh
  S-->>C: IncidentDetail DTO
  C-->>E: 201 Created
```

## 2. Use case diagram

```mermaid
flowchart LR
  Emp([Employee])
  Tech([Technician])
  Lead([Team Lead])
  Admin([Administrator])
  Mgr([IT Manager])
  Sys([SLA Monitor / System])

  subgraph I-ITSM
    UC1(Register / log in)
    UC2(Report incident)
    UC3(Track own incidents)
    UC4(Provide requested info)
    UC5(Verify or reopen resolution)
    UC6(Give feedback)
    UC7(View queue & workload)
    UC8(Acknowledge / investigate)
    UC9(Add notes / request info)
    UC10(Resolve incident)
    UC11(Assign / reassign)
    UC12(Merge duplicates)
    UC13(Manage users & technicians)
    UC14(Manage services & categories)
    UC15(Configure SLA policies)
    UC16(Switch strategies)
    UC17(View reports & audit log)
    UC18(Monitor SLA & escalate)
    UC19(Receive escalations)
  end

  Emp --- UC1 & UC2 & UC3 & UC4 & UC5 & UC6
  Tech --- UC1 & UC7 & UC8 & UC9 & UC10 & UC12
  Lead --- UC11 & UC17 & UC19
  Lead -. inherits .-> Tech
  Admin --- UC11 & UC13 & UC14 & UC15 & UC16 & UC17 & UC19
  Mgr -. inherits .-> Admin
  Sys --- UC18
  UC18 -. notifies .-> UC19
```

## 3. Class diagram (core domain)

```mermaid
classDiagram
  direction TB
  class BaseEntity {
    <<abstract>>
    -Long id
    -Instant createdAt
    -Instant updatedAt
  }
  class User {
    <<abstract>>
    -String userCode
    -String fullName
    -String email
    -String passwordHash
    -boolean active
    -int failedLoginAttempts
    -Instant lockedUntil
    +getRole()* Role
    +permissions()* Set~Permission~
    +can(Permission) boolean
    +recordFailedLogin(now, max, lock) boolean
    +recordSuccessfulLogin(now)
  }
  class Employee
  class Technician {
    -int experienceYears
    -int maxConcurrentIncidents
    -AvailabilityStatus availability
    +hasSkill(Category) boolean
    +hasCapacity(int active) boolean
    +loadRatio(int active) double
  }
  class TeamLead {
    +permissions() Set~Permission~
  }
  class Administrator
  class ITManager
  BaseEntity <|-- User
  User <|-- Employee
  User <|-- Technician
  Technician <|-- TeamLead
  User <|-- Administrator
  Administrator <|-- ITManager
  TeamLead o-- "0..*" Technician : team
  Technician "0..*" --> "0..*" Category : skills
  Technician "0..*" --> "0..*" ITService : expertise

  class Category {
    -String code
    -String name
    -Set~String~ keywords
  }
  class ITService {
    -String code
    -BusinessCriticality criticality
    +addDependency(ITService)
    +transitiveDependencies() Set~ITService~
  }
  ITService "0..*" --> "0..*" ITService : dependsOn
  ITService --> Category : defaultCategory

  class Incident {
    -String number
    -String title
    -Impact impact
    -Urgency urgency
    -Priority priority
    -IncidentStatus status
    -int escalationLevel
    +currentState() IncidentState
    +allowedActions() Set~IncidentAction~
    +classify(Category, Instant)
    +assignTo(Technician, Instant)
    +acknowledge(Instant)
    +resolve(Resolution, Instant)
    +close(ClosureCode, String, Instant)
    +markDuplicateOf(Incident, Instant)
    ~changeStatus(IncidentStatus)
  }
  BaseEntity <|-- Incident
  Incident --> "1" User : reporter
  Incident --> "0..1" Technician : assignedTo
  Incident --> "1" ITService : affectedService
  Incident --> "0..1" Category
  Incident *-- "1" SlaRecord
  Incident *-- "0..1" Resolution
  Incident *-- "0..1" Feedback
  Incident *-- "0..*" IncidentNote
  Incident --> "0..1" Incident : duplicateOf

  class SlaRecord {
    -Instant responseDeadline
    -Instant resolutionDeadline
    -boolean responseBreached
    -boolean resolutionBreached
    -Instant pausedAt
    +start(SlaPolicy, Instant)$ SlaRecord
    +pause(Instant)
    +resume(Instant)
    +status(Instant, Instant) SlaStatus
  }
  class SlaPolicy {
    -Priority priority
    -int responseMinutes
    -int resolutionMinutes
  }
  SlaPolicy o-- "0..1" ITService : override for

  class IncidentState {
    <<abstract>>
    +status()* IncidentStatus
    +allowedActions()* Set~IncidentAction~
    ~classify(Incident, Category, Instant)
    ~assign(Incident, Technician, Instant)
    ~acknowledge(Incident, Instant)
    ~resolve(Incident, Resolution, Instant)
    ~close(Incident, ClosureCode, String, Instant)
  }
  class ActiveWorkState {
    <<abstract>>
    ~assign(Incident, Technician, Instant)
  }
  IncidentState <|-- NewState
  IncidentState <|-- ClassifiedState
  IncidentState <|-- ActiveWorkState
  ActiveWorkState <|-- AssignedState
  ActiveWorkState <|-- AcknowledgedState
  ActiveWorkState <|-- InProgressState
  IncidentState <|-- PendingInfoState
  IncidentState <|-- ResolvedState
  IncidentState <|-- VerifiedState
  IncidentState <|-- ClosedState
  Incident ..> IncidentState : delegates to
```

**Engines, events and notifications (interfaces and patterns)**

```mermaid
classDiagram
  direction LR
  class NamedStrategy {
    <<interface>>
    +key() String
    +displayName() String
  }
  class MatrixPriorityStrategy
  class WeightedScoringPriorityStrategy
  class PriorityStrategy {
    <<interface>>
    +assess(PriorityContext) PriorityAssessment
  }
  class AssignmentStrategy {
    <<interface>>
    +rank(Incident, List) List
    +select(Incident, List) Optional
  }
  NamedStrategy <|-- PriorityStrategy
  NamedStrategy <|-- AssignmentStrategy
  PriorityStrategy <|.. MatrixPriorityStrategy
  PriorityStrategy <|.. WeightedScoringPriorityStrategy
  class AbstractScoringAssignmentStrategy {
    <<abstract>>
    +rank(Incident, List) List
    #isEligible(Incident, TechnicianCandidate) boolean
    #evaluate()* TechnicianRecommendation
  }
  AssignmentStrategy <|.. AbstractScoringAssignmentStrategy
  AbstractScoringAssignmentStrategy <|-- SkillBasedAssignmentStrategy
  AbstractScoringAssignmentStrategy <|-- LoadBalancedAssignmentStrategy
  AbstractScoringAssignmentStrategy <|-- SmartScoringAssignmentStrategy
  AssignmentStrategy <|.. RoundRobinAssignmentStrategy
  class StrategyRegistry~T~ {
    +get(String key) T
  }
  StrategyRegistry o-- NamedStrategy

  class EscalationHandler {
    <<abstract>>
    -EscalationHandler next
    +linkWith(EscalationHandler)
    +escalate(Incident, level)
  }
  EscalationHandler <|-- TeamLeadEscalationHandler
  EscalationHandler <|-- AdministratorEscalationHandler
  EscalationHandler <|-- ITManagerEscalationHandler

  class IncidentEventPublisher {
    <<interface>>
    +publish(IncidentEvent)
  }
  class IncidentEventListener {
    <<interface>>
    +onEvent(IncidentEvent)
  }
  IncidentEventPublisher <|.. InProcessEventBus
  InProcessEventBus o-- IncidentEventListener
  IncidentEventListener <|.. AuditTrailListener
  IncidentEventListener <|.. NotificationListener
  IncidentEventListener <|.. RealtimeUpdateListener
  class IncidentEvent {
    <<abstract>>
    +eventType()* String
  }
  IncidentEvent <|-- Created
  IncidentEvent <|-- Assigned
  IncidentEvent <|-- StatusChanged
  IncidentEvent <|-- SlaBreached
  IncidentEvent <|-- Escalated

  class NotificationChannel {
    <<interface>>
    +supports(User, Message) boolean
    +deliver(User, Message) DeliveryStatus
  }
  NotificationChannel <|.. InAppNotificationChannel
  NotificationChannel <|.. EmailNotificationChannel
  NotificationChannel <|.. SmsNotificationChannel
  class EmailGateway {
    <<interface>>
    +send(to, subject, body) DeliveryStatus
  }
  class SmsGateway {
    <<interface>>
    +sendSms(phone, text) DeliveryStatus
  }
  EmailNotificationChannel --> EmailGateway
  SmsNotificationChannel --> SmsGateway
  EmailGateway <|.. SmtpEmailGateway
  EmailGateway <|.. LoggingEmailGateway
  SmsGateway <|.. SimulatedSmsGatewayAdapter
  SimulatedSmsGatewayAdapter --> ThirdPartySmsClient : adapts
  NotificationChannelFactory o-- NotificationChannel
```

**Exception hierarchy**

```mermaid
classDiagram
  RuntimeException <|-- ItsmException
  class ItsmException {
    <<abstract>>
    +type()* ErrorType
    +getErrorCode() String
  }
  ItsmException <|-- AuthenticationFailedException
  ItsmException <|-- AccountLockedException
  ItsmException <|-- UnauthorizedOperationException
  ItsmException <|-- ResourceNotFoundException
  ResourceNotFoundException <|-- IncidentNotFoundException
  ResourceNotFoundException <|-- ServiceNotFoundException
  ResourceNotFoundException <|-- UserNotFoundException
  ItsmException <|-- InvalidStateTransitionException
  ItsmException <|-- TechnicianUnavailableException
  ItsmException <|-- WorkloadExceededException
  ItsmException <|-- SlaConfigurationMissingException
  ItsmException <|-- DuplicateIncidentException
  ItsmException <|-- BusinessRuleViolationException
  ItsmException <|-- InvalidInputException
  ItsmException <|-- ResourceAlreadyExistsException
  ItsmException <|-- ExternalServiceException
```

## 4. Incident lifecycle (State pattern)

```mermaid
stateDiagram-v2
  [*] --> NEW : report
  NEW --> CLASSIFIED : classify (auto)
  CLASSIFIED --> ASSIGNED : assign (auto/manual)
  ASSIGNED --> ACKNOWLEDGED : acknowledge (SLA response recorded)
  ACKNOWLEDGED --> IN_PROGRESS : start work
  IN_PROGRESS --> PENDING_INFO : request info (SLA paused)
  PENDING_INFO --> IN_PROGRESS : provide info (SLA resumed, deadline extended)
  IN_PROGRESS --> RESOLVED : resolve
  RESOLVED --> IN_PROGRESS : reopen
  RESOLVED --> VERIFIED : reporter confirms
  VERIFIED --> CLOSED : close
  ACKNOWLEDGED --> ASSIGNED : reassign
  IN_PROGRESS --> ASSIGNED : reassign
  NEW --> CLOSED : mark duplicate
  CLASSIFIED --> CLOSED : mark duplicate
  ASSIGNED --> CLOSED : mark duplicate
  IN_PROGRESS --> CLOSED : mark duplicate
  CLOSED --> [*]
```

Every other combination (for example NEW → CLOSED via *close*, or RESOLVED → resolve)
throws `InvalidStateTransitionException`. The default method on the abstract `IncidentState`
rejects the action, and only the states that permit it override the method. The UI hides
buttons using `allowedActions()`, but the domain enforces the rule regardless.

## 5. Database design (ER diagram)

```mermaid
erDiagram
  USERS ||--o{ INCIDENTS : reports
  USERS ||--o{ INCIDENTS : "assigned to"
  USERS ||--o{ USERS : "team lead of"
  USERS }o--o{ CATEGORIES : "technician_skills"
  USERS }o--o{ IT_SERVICES : "technician_services"
  USERS ||--o{ NOTIFICATIONS : receives
  USERS ||--o{ INCIDENT_NOTES : writes
  CATEGORIES ||--o{ CATEGORY_KEYWORDS : has
  CATEGORIES ||--o{ INCIDENTS : classifies
  IT_SERVICES ||--o{ INCIDENTS : affected
  IT_SERVICES }o--o{ IT_SERVICES : "service_dependencies"
  IT_SERVICES ||--o{ SLA_POLICIES : overrides
  INCIDENTS ||--o{ INCIDENT_NOTES : contains
  INCIDENTS ||--o| INCIDENTS : "duplicate of"
  USERS {
    bigint id PK
    varchar user_type "discriminator"
    varchar user_code UK
    varchar email UK
    varchar password_hash
    int experience_years "technician"
    int max_concurrent_incidents "technician"
    bigint team_lead_id FK
  }
  INCIDENTS {
    bigint id PK
    varchar incident_number UK
    varchar status
    varchar priority
    varchar impact
    varchar urgency
    bigint reporter_id FK
    bigint assigned_to_id FK
    bigint service_id FK
    bigint category_id FK
    timestamptz sla_response_deadline
    timestamptz sla_resolution_deadline
    boolean sla_resolution_breached
    int escalation_level
    bigint version "optimistic lock"
  }
  SLA_POLICIES {
    bigint id PK
    varchar priority
    int response_minutes
    int resolution_minutes
    bigint service_id FK "null = global"
  }
  AUDIT_ENTRIES {
    bigint id PK
    timestamptz occurred_at
    varchar actor_name
    varchar action
    varchar entity_type
    bigint entity_id
    varchar previous_state
    varchar new_state
    varchar description
  }
  NOTIFICATIONS {
    bigint id PK
    bigint recipient_id FK
    varchar channel
    varchar delivery_status
    boolean is_read
  }
  SYSTEM_SETTINGS {
    varchar setting_key PK
    varchar setting_value
  }
```

Notes:
- **Single-table inheritance** for `users`. The discriminator `user_type` maps to the concrete
  Java subclass. It was chosen over joined tables because the subclasses add only a few
  columns, and polymorphic queries are frequent.
- `SlaRecord`, `Resolution` and `Feedback` are `@Embeddable` (composition), so they are stored
  as columns of `incidents`.
- `audit_entries` keeps denormalised actor data, so the history survives user changes.
- Indexes cover every search filter (status, priority, service, category, assignee, reporter,
  reported_at).
- The schema is owned by Flyway (`V1__initial_schema.sql`). Hibernate runs in `validate` mode,
  and the test suite runs the same migration, so drift between entities and schema fails the build.

## 6. Algorithms and rationale

### 6.1 Priority determination (two interchangeable strategies)
- **Matrix (default):** `score = Impact × Urgency × BusinessCriticality`, each factor 1–4, so
  the score ranges 1–64. P1 ≥ 32, P2 ≥ 16, P3 ≥ 6, otherwise P4.
  Multiplication lets one low factor pull the priority down sharply: a "critical" problem on a
  low-criticality service is not a P1.
- **Weighted:** a 0–100 score = 35% impact + 30% urgency + 20% criticality + 15% user reach
  (`log10(users)/3`). It adds **+8** when the same service had 3 or more incidents in the last
  24 hours, which signals a recurring or systemic problem. This is historical-data-based
  prioritisation. Bands: P1 ≥ 75, P2 ≥ 55, P3 ≥ 35, otherwise P4.
- Each assessment stores a human-readable **rationale**, shown on the incident and in the audit trail.

### 6.2 Technician assignment (four interchangeable strategies)
| Strategy | Rule |
|---|---|
| Skill-based | Only technicians skilled in the category; the least-loaded wins |
| Load-balanced | Lowest workload ratio wins; matching skill gives a +0.1 bonus |
| Round-robin | Per-category rotation among skilled technicians with capacity |
| **Smart (default)** | 0.40 skill + 0.25 spare capacity + 0.15 experience + 0.10 past success on the service + 0.10 priority fit (seniors for P1/P2) |

Every strategy excludes technicians who are unavailable or at capacity. A manual assignment
beyond capacity raises `WorkloadExceededException`.

### 6.3 SLA management
- The applicable policy is the service-specific one if it exists, otherwise the global default.
  If neither exists, `SlaConfigurationMissingException` is raised.
- Deadlines are computed from the reported time. The clock **pauses in PENDING_INFO**, and on
  resume both deadlines move by the paused duration.
- The background monitor runs every 60 seconds. It raises a one-time warning at the policy's
  threshold (75%), records response and resolution breaches once each, and escalates.
- **Escalation (Chain of Responsibility):** an unacknowledged P1/P2 escalates to level N after N×
  the response target (Team Lead → Administrator → IT Manager). A resolution breach escalates
  one more level. A level with nobody available is skipped upward.

### 6.4 Intelligent features (A–G)
| | Feature | How |
|---|---|---|
| A | Duplicate detection | Tokenise, remove stop words, map domain synonyms ("unavailable/failing/unable" → `fail`), stem, then compute TF-IDF cosine. Score = 0.6 text + 0.3 service relation + 0.1 time proximity. ≥ 0.50 marks a suspected duplicate. The same reporter submitting text ≥ 85% similar within 30 minutes raises `DuplicateIncidentException` |
| B | Correlation | Each open incident votes for its service and the service's direct dependencies. Services are taken greedily by vote count. A cluster of 3 or more is a **major incident**: one alert per root service per hour |
| C | Automatic escalation | SLA monitor plus the escalation chain (6.3) |
| D | Smart recommendation | Smart scoring strategy, with explainable reasons per candidate |
| E | SLA risk prediction | Historical mean time-to-resolve (same priority and category), adjusted for time spent, assignee workload and unassigned status. risk = 0.5 × elapsed + 0.5 × pressure |
| F | Auto-categorisation | Keyword scoring from admin-maintained category keywords (title × 2, description × 1, plus the service's default category). Pluggable `CategorySuggester` interface, ready for an AI implementation |
| G | Suggested resolution | TF-IDF similarity against resolved history (same category gets +0.1). The top 3 can be applied with one click |

## 7. Security design
- **Authentication:** BCrypt password hashes; stateless HMAC-SHA256 JWT (8 h); generic login
  errors; constant-time path for unknown e-mails; **lockout** for 15 minutes after 5 failures.
- **Authorization:** three layers.
  1. URL rules (`/api/admin/**` is admin only).
  2. `@PreAuthorize` on permissions that are **derived polymorphically from the `User` subclass**.
  3. Object-level checks in `IncidentAccessPolicy`: reporters see only their own incidents,
     only the assignee works an incident, and internal notes are hidden from reporters.
- **Input validation:** Bean Validation on every DTO, plus domain invariants in constructors
  (defence in depth).
- **API hardening:** CORS allow-list, no sessions, so CSRF is not applicable; frame-deny header;
  RFC 7807 errors with no stack traces; CSV export neutralises spreadsheet formula injection;
  optimistic locking prevents lost updates.
- **WebSocket:** the JWT is validated on STOMP CONNECT, and user queues are private.

## 8. Scalability
- Stateless API (JWT), so it scales horizontally behind a load balancer.
- `IncidentEventPublisher` is an interface. Swapping the in-process bus for Kafka or RabbitMQ
  makes notifications and audit asynchronous services (event-driven microservices) without
  touching the publishers.
- Indexed queries, paginated endpoints, entity graphs to avoid N+1 queries, cached historical
  statistics, and Java 21 virtual threads for request handling.
- WebSocket simple broker → an external STOMP broker relay for multi-instance deployments.
