# I-ITSM — Test Cases

**Automated suite.**
- Backend: **62 tests** with ~79% instruction coverage (JaCoCo). Run with `cd backend && ./mvnw verify`.
- Frontend: **8 tests** (Vitest + Testing Library). Run with `cd frontend && npm test`.

Every case below is automated. The *Test* column names the JUnit method that implements it.
Integration tests boot the whole application (Flyway schema + seeded data) and call the REST
API through MockMvc.

| ID | Category | Scenario | Input / steps | Expected result | Test |
|---|---|---|---|---|---|
| TC-01 | Normal operation | Full lifecycle | NEW → classify → assign → acknowledge → start → resolve → verify → close | Status CLOSED, SLA response recorded, SLA not breached, only FEEDBACK allowed | `IncidentLifecycleTest.fullLifecycle` |
| TC-02 | Invalid state transition | Close a NEW incident | `close()` on NEW | `InvalidStateTransitionException`, status unchanged | `IncidentLifecycleTest.newCannotBeClosed` |
| TC-03 | Invalid state transition | Resolve before work starts | `resolve()` on ASSIGNED | `InvalidStateTransitionException` | `IncidentLifecycleTest.cannotResolveFromAssigned` |
| TC-04 | Normal / SLA | SLA calculation | P1 policy 5/30 min from T0 | Response deadline T0+5m, resolution T0+30m, ON_TRACK | `SlaRecordTest.deadlines` |
| TC-05 | Boundary | Breach exactly at the deadline | Check at T0+5m and at T0+5m+1s | Not breached at the deadline; breached 1 s later; recorded only once | `SlaRecordTest.responseBreachBoundary` |
| TC-06 | Boundary / business rule | Priority thresholds | 7 combinations incl. scores 64, 32, 27, 16, 6, 4, 1 | P1 at ≥32, P2 at ≥16, P3 at ≥6, else P4 | `PriorityStrategyTest.matrixBoundaries` |
| TC-07 | Business rule | Skill-based assignment | 4 technicians, mixed skills and load | Only skilled technicians with capacity, least loaded first | `AssignmentStrategyTest.skillBased` |
| TC-08 | Exception (technician unavailable) | All suitable technicians on leave, offline or full | `select()` on 3 strategies | Empty selection, nobody unavailable chosen | `AssignmentStrategyTest.unavailableTechniciansExcluded` |
| TC-09 | Normal / intelligent | Duplicate detection (spec example) | "Payment application unavailable", "Payment transaction failing" vs "Unable to make payment" | Both payment incidents matched, unrelated one not | `IntelligenceEngineTest.specPaymentExample` |
| TC-10 | Invalid login | Account lockout | 5 failed attempts | Locked for 15 min, unlocked afterwards | `UserModelTest.lockout` |
| TC-11 | Normal / authentication | Valid login | Correct credentials | 200, JWT, role and permissions, no password hash in the response | `AuthApiTest.loginSuccess` |
| TC-12 | Invalid login (API) | Wrong password ×5 | 4 × 401 `INVALID_CREDENTIALS`, then 423 `ACCOUNT_LOCKED` | Correct password also refused while locked | `AuthApiTest.invalidLoginAndLockout` |
| TC-13 | Invalid user input | Weak password, bad e-mail at registration | `password=short`, `email=not-an-email` | 400 `VALIDATION_FAILED` with field errors | `AuthApiTest.registrationValidation` |
| TC-14 | Authorization | Unauthenticated and wrong role | No token; forged token; employee calls admin API | 401, 401, 403; admin gets 200 | `AuthApiTest.authorization` |
| TC-15 | Normal (Workflow 1) | Report → prioritise → assign → notify | Employee reports a Payment incident (High/Critical) | Category APPLICATION, P1, "Payment Gateway P1" SLA, ASSIGNED, audit sequence, assignee notified | `IncidentWorkflowApiTest.workflow1ReportPrioritiseAssignNotify` |
| TC-16 | Normal (Workflow 2) | Investigate → SLA → resolve → verify → close | Acknowledge, start, request info (SLA paused), provide info, internal note, resolve, verify, feedback, close | Each status correct; internal note hidden from reporter; SLA MET; reporter notified | `IncidentWorkflowApiTest.workflow2InvestigateResolveVerifyClose` |
| TC-17 | Normal (Workflow 3) | Duplicates → correlation → merge | 3 payment reports by different employees | 2nd and 3rd flagged as suspected duplicates; one major-incident cluster contains them; merge closes as DUPLICATE | `IncidentWorkflowApiTest.workflow3DuplicatesAndCorrelation` |
| TC-18 | Exception (duplicate incident) | Same reporter resubmits | Identical report twice, then `force=true` | 409 `DUPLICATE_INCIDENT` listing existing numbers; forced submit 201 | `IncidentWorkflowApiTest.sameReporterDuplicateBlocked` |
| TC-19 | Authorization (object level) | Other users' incidents | Another employee views; employee acknowledges; unassigned technician acknowledges; employee search with someone else's reporterId | 403, 403, 403; search returns only own incidents | `IncidentWorkflowApiTest.objectLevelAuthorization` |
| TC-20 | Invalid state transition (API) | Verify an unresolved incident | `POST /verify` on ASSIGNED | 409 `INVALID_STATE_TRANSITION`, attemptedAction=VERIFY | `IncidentWorkflowApiTest.invalidTransition` |
| TC-21 | Invalid input / service not found | Malformed body; unknown service id | Short title, invalid enum; serviceId 99999 | 400; 404 `SERVICE_NOT_FOUND` | `IncidentWorkflowApiTest.invalidInput` |
| TC-22 | Exception (workload exceeded) | Assign beyond capacity | Technician with max 1, assign 2 incidents | First 200, second 422 `WORKLOAD_EXCEEDED` | `IncidentWorkflowApiTest.workloadExceeded` |
| TC-23 | Business rule (SLA monitoring) | Background SLA scan | Run `/admin/sla/scan` on seeded overdue incidents | Breaches detected and escalations recorded; risk list available | `IncidentWorkflowApiTest.slaMonitoring` |
| TC-24 | Normal (search) | Multi-criteria filter | status=CLOSED, priority P2/P3, service=EMAIL; invalid sort field | Every row matches all filters; invalid sort → 400 | `IncidentWorkflowApiTest.searchFilters` |
| TC-25 | Business rule / authorization | Runtime strategy switch | Admin sets round-robin; unknown key; team lead attempts the change | 200 and active; 400; 403 | `IncidentWorkflowApiTest.switchStrategy` |

**Additional automated cases** (beyond the 25 documented above):
- **Domain**
  - Reopen counts and clears resolution.
  - PENDING_INFO pauses and extends the SLA.
  - Duplicate rules (no self or chained duplicates).
  - Feedback rules (1–5, once, only after resolution).
  - Reassignment returns the incident to ASSIGNED.
  - Title and description length boundaries.
- **SLA**
  - Warning only once at 75%.
  - MET vs BREACHED.
  - Late response.
  - Paused clock never breaches.
- **Users**
  - Factory and polymorphic permissions.
  - Deactivated users lose all permissions.
  - Capacity boundaries (1–50; 49 has capacity, 50 does not).
- **Engines**
  - Weighted priority reacts to affected users and recurrence.
  - Strategy registry rejects unknown keys.
  - Load-balanced and round-robin rotation.
  - Smart strategy prefers senior engineers for P1.
  - Keyword categorisation.
  - Correlation finds the root service.
  - SLA risk LOW vs HIGH; a paused clock is never predicted as breached.
  - Dependency cycle rejected.
  - Text analyser synonyms.
- **API**
  - Registration and duplicate e-mail (409).
  - Dashboard aggregates.
- **Smoke**
  - Full context boots, the schema validates and demo data is seeded.
- **Frontend**
  - Formatting helpers.
  - Query-string builder.
  - Badges show text labels, not colour alone.
  - SLA countdown: a paused clock never shows "overdue"; a response-only breach is labelled as such.
- **Notifications**
  - SMTP adapter: demo-domain recipients are simulated, real ones sent, provider errors translated.

## Exception coverage matrix (brief → implementation)

| Situation in brief | Exception / response | Covered by |
|---|---|---|
| Invalid login | `AuthenticationFailedException` → 401; `AccountLockedException` → 423 | TC-10, TC-12 |
| Unauthorized operation | `UnauthorizedOperationException` / `AccessDenied` → 403 | TC-14, TC-19, TC-25 |
| Invalid incident | Domain validation `InvalidInputException` and Bean Validation → 400 | TC-13, TC-21, `detailValidation` |
| Invalid state transition | `InvalidStateTransitionException` → 409 | TC-02, TC-03, TC-20 |
| Technician unavailable | `TechnicianUnavailableException` → 409 | TC-08 (engine), manual assign path |
| Technician workload exceeded | `WorkloadExceededException` → 422 | TC-22 |
| Service not found | `ServiceNotFoundException` → 404 | TC-21 |
| Missing SLA configuration | `SlaConfigurationMissingException` → 422 | Resolver path; default policies cannot be deactivated (BR-02) |
| Duplicate incident | `DuplicateIncidentException` → 409 | TC-18 |
| Database failure | `DataAccessResourceFailureException` / `CannotCreateTransactionException` → 503 `DATABASE_UNAVAILABLE` | Global handler |
| Network failure | `ExternalServiceException` (SMTP/SMS) → logged as FAILED delivery, workflow continues | `NotificationDispatcher` |
| Invalid user input | `MethodArgumentNotValidException` → 400 with field errors | TC-13, TC-21 |
| Concurrent edits | `OptimisticLockingFailureException` → 409 `CONCURRENT_MODIFICATION` | Global handler (`@Version`) |
