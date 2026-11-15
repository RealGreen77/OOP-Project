# I-ITSM — Problem Analysis & Requirements Specification

## 1. Problem analysis

**Problem.** Organisations depend on many IT services: payments, identity and SSO, e-mail,
VPN, ERP, storage and others. When one fails, incidents are reported through ad-hoc channels
(phone, e-mail, chat). Without a system, the organisation suffers from:
- delayed responses;
- inconsistent prioritisation;
- uneven technician workload;
- missed SLAs;
- duplicate reports of the same outage;
- no history and no visibility into recurring problems.

**Stakeholders**

| Stakeholder | Interest |
|---|---|
| Employees / customers | Report problems easily, see progress, get them fixed quickly |
| Technicians / support engineers | A clear, prioritised queue, context and history, no duplicate work |
| Team leads | Balanced workload, early warning of SLA risk, escalation point |
| Administrators / IT managers | Configure services, SLAs and routing; oversight via dashboards and audit |
| Business owners of services | Reliable services; revenue-critical services get the strictest SLAs |
| Auditors / compliance | Immutable record of who did what and when |

**Existing challenges addressed.** Each challenge maps to the feature that solves it.

| Challenge | Solution |
|---|---|
| Delayed response | Automatic classification and assignment |
| Incorrect prioritisation | Consistent priority algorithms |
| Inefficient technician allocation | Workload-aware assignment strategies |
| SLA violations | SLA timers, warnings and escalation |
| Duplicate reports | Duplicate detection |
| Poor visibility into service health | Correlation and major-incident detection, dashboards |
| Incomplete history | Audit trail and suggested resolutions |

**Proposed solution.** A web-based ITSM platform:
- **Architecture:** a layered, object-oriented backend (REST plus WebSocket) and a role-specific
  React frontend.
- **Automation:** classification, prioritisation, SLA management, technician recommendation and
  escalation are automated with pluggable algorithms.
- **Event-driven core:** a single source of events drives auditing, notifications and live dashboards.

**Assumptions**
1. Users authenticate with organisation e-mail and password. SSO/OAuth integration is future work.
2. Employees self-register. Support staff and administrators are created by administrators.
3. SLA clocks run 24×7, not in business hours, and pause only while waiting for the reporter.
4. E-mail is delivered over SMTP (Mailpit in the demo). SMS goes through a simulated vendor
   gateway, which the brief permits.
5. One technician owns an incident at a time. Team leads and administrators may act on any incident.
6. Category keywords maintained by administrators are an acceptable basis for auto-categorisation.
   AI-based categorisation is a planned extension behind the existing interface.
7. The response SLA is met when the assigned technician acknowledges the incident.

## 2. User roles

| Role | Inherits | Key capabilities |
|---|---|---|
| Employee | — | Register/log in, report, track, add info/comments, verify/reopen, give feedback |
| Technician | — | View queue, acknowledge, investigate, add notes (internal or public), request info, resolve, take ownership of unassigned incidents, set availability |
| Team Lead | Technician | Assign/reassign, team workload, reports, receives level-1 escalations |
| Administrator | — | Manage users/technicians, services, categories, SLA policies, assignment and priority strategies; monitor incidents, reports, audit log, SLA violations, notification log |
| IT Manager | Administrator | Everything an administrator can do; receives level-3 (final) escalations |

## 3. Functional requirements

| ID | Requirement |
|---|---|
| FR-01 | Users register (employees) and log in; the system returns a JWT and role-specific permissions |
| FR-02 | An employee reports an incident with title, description, affected service, impact, urgency, users affected and an optional category |
| FR-03 | The system creates the incident with a unique number (INC####) and status NEW |
| FR-04 | The system classifies the incident automatically: reporter choice, else keyword suggestion, else the service default |
| FR-05 | The system calculates priority P1–P4 with the active priority strategy and records the rationale |
| FR-06 | The system determines the applicable SLA (service override, else global default) and computes response and resolution deadlines |
| FR-07 | The system recommends and (if enabled) assigns a technician with the active assignment strategy |
| FR-08 | The system notifies relevant users on create, assign, priority change, info request, resolve, close, SLA warning, SLA breach, escalation, duplicate and major incident |
| FR-09 | Technicians acknowledge, start work, add notes, request information, resolve; reporters provide info, verify or reopen; staff close |
| FR-10 | The system enforces the lifecycle and rejects invalid transitions |
| FR-11 | The SLA monitor tracks response and resolution time, warns before deadlines, detects and records violations, and escalates |
| FR-12 | Every significant action is recorded in an immutable audit trail (timestamp, user, action, incident, previous/new state, description) |
| FR-13 | Users search and filter incidents by number/text, date range, status, priority, category, service, technician, SLA status, escalation and assignment; staff export CSV |
| FR-14 | Dashboards show the incident overview, technician workload, service, category and priority analysis, SLA compliance and trends |
| FR-15 | The system detects likely duplicates and blocks repeat submissions by the same reporter (with an override) |
| FR-16 | The system correlates open incidents by shared service and dependencies, and alerts on major incidents |
| FR-17 | The system predicts SLA breach risk for active incidents |
| FR-18 | The system suggests resolutions from similar historical incidents |
| FR-19 | Administrators manage users, technician skills and capacity, services and dependencies, categories and keywords, SLA policies, and active strategies at runtime |
| FR-20 | Users manage their profile, notification preferences (e-mail, SMS) and password |

## 4. Non-functional requirements

| ID | Category | Requirement |
|---|---|---|
| NFR-01 | Security | BCrypt hashing, JWT authentication, role, permission and ownership authorization, account lockout, input validation, no sensitive data in responses |
| NFR-02 | Maintainability | Layered architecture; patterns isolate change (new strategy, notification channel, category or state = additive change) |
| NFR-03 | Extensibility | New categories and services are data; new algorithms and channels are new beans; event listeners plug into the bus |
| NFR-04 | Reliability | Transactions keep incident and audit consistent; optimistic locking; notification failures never break the workflow |
| NFR-05 | Performance | Paginated APIs; indexed filters; no N+1 queries on hot paths; typical API response < 300 ms on seeded data |
| NFR-06 | Usability | Role-specific navigation; only legal actions are shown; live updates; clear error messages; dark mode; responsive layout |
| NFR-07 | Observability | Health and metrics endpoints, structured logs, notification delivery log, audit log |
| NFR-08 | Portability | Docker Compose deployment; configuration through environment variables |
| NFR-09 | Testability | Time injected via `Clock`; pure algorithm classes; ≥ 15 documented test cases (70 automated: 62 backend + 8 frontend) |
| NFR-10 | Accessibility | Status is never shown by colour alone (labels and icons); charts have table views; keyboard-reachable controls |

## 5. Business rules

| ID | Rule |
|---|---|
| BR-01 | Priority = active strategy(impact, urgency, service criticality[, users, history]). The matrix default is P1 ≥ 32, P2 ≥ 16, P3 ≥ 6 |
| BR-02 | A service-specific SLA policy overrides the global default for the same priority. Every priority must have an active global default, which cannot be deactivated |
| BR-03 | Only one active SLA policy may exist per (priority, service) |
| BR-04 | The SLA response is met at acknowledgement. The SLA clock pauses in PENDING_INFO and deadlines extend on resume |
| BR-05 | Unacknowledged P1/P2 incidents escalate one level per elapsed response window (max level 3). A resolution breach escalates one level |
| BR-06 | A technician cannot exceed `maxConcurrentIncidents`. Technicians on leave or offline are never auto-assigned |
| BR-07 | Only the assignee (or a team lead or administrator) may work an incident. Only the reporter may provide information and give feedback. The reporter (or an administrator) verifies or reopens |
| BR-08 | Reopening returns the incident to IN_PROGRESS with the same technician and increments the reopen count |
| BR-09 | Closing requires VERIFIED, except closing as a duplicate, which is allowed from any active state |
| BR-10 | A duplicate cannot point to itself or to another duplicate |
| BR-11 | The same reporter submitting text ≥ 85% similar within 30 minutes is rejected unless `force=true` |
| BR-12 | Feedback is rated 1–5, once per incident, and only after resolution (not for duplicates) |
| BR-13 | Five consecutive failed logins lock the account for 15 minutes |
| BR-14 | Internal notes are never visible to reporters |
| BR-15 | Audit entries are immutable and written in the same transaction as the change |
| BR-16 | A technician with active incidents cannot be deactivated until the incidents are reassigned |
