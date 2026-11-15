package com.itsm.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end workflows from the project brief, plus business-rule, authorization and exception cases. */
class IncidentWorkflowApiTest extends ApiTestBase {

    @Test
    @DisplayName("TC-15 Workflow 1: employee reports -> system classifies, prioritises, applies SLA, assigns, notifies")
    void workflow1ReportPrioritiseAssignNotify() throws Exception {
        JsonNode incident = read(postAs(KARAN, "/api/incidents", report("Checkout page throws error for all customers",
                "Customers cannot complete checkout, the checkout page throws an error after entering card details.",
                "PAYMENT", "HIGH", "CRITICAL", 400)).andExpect(status().isCreated()));

        assertThat(incident.path("number").asText()).startsWith("INC");
        assertThat(incident.path("category").path("code").asText()).isEqualTo("APPLICATION"); // auto-categorised
        assertThat(incident.path("priority").asText()).isEqualTo("P1");                     // 3 x 4 x 4 = 48
        assertThat(incident.path("sla").path("policyName").asText()).isEqualTo("Payment Gateway P1"); // service override
        assertThat(incident.path("status").asText()).isEqualTo("ASSIGNED");
        String assignee = incident.path("assignedTo").path("userCode").asText();
        assertThat(assignee).isNotBlank();

        long id = incident.path("id").asLong();
        List<String> actions = new ArrayList<>();
        read(getAs(KARAN, "/api/incidents/" + id + "/history")).forEach(a -> actions.add(a.path("action").asText()));
        assertThat(actions).containsSubsequence("INCIDENT_CREATED", "INCIDENT_CLASSIFIED", "PRIORITY_CALCULATED",
                "SLA_APPLIED", "INCIDENT_ASSIGNED");

        JsonNode inbox = read(getAs(TECH_EMAILS.get(assignee), "/api/notifications"));
        assertThat(inbox.path("content").findValuesAsText("incidentNumber")).contains(incident.path("number").asText());
    }

    @Test
    @DisplayName("TC-16 Workflow 2: technician investigates -> SLA tracked -> resolved -> user verifies -> closed")
    void workflow2InvestigateResolveVerifyClose() throws Exception {
        Map<String, Object> body = new HashMap<>(report("Wi-Fi not working in conference room B",
                "The Wi-Fi network in conference room B is not reachable from any laptop since this morning.",
                "NETWORK", "MEDIUM", "HIGH", 12));
        body.put("categoryId", categoryId("NETWORK"));
        JsonNode created = read(postAs(EMPLOYEE, "/api/incidents", body).andExpect(status().isCreated()));
        long id = created.path("id").asLong();
        String base = "/api/incidents/" + id;
        String tech = TECH_EMAILS.get(created.path("assignedTo").path("userCode").asText());

        postAs(tech, base + "/acknowledge", null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACKNOWLEDGED"))
                .andExpect(jsonPath("$.sla.respondedAt").exists());
        postAs(tech, base + "/start", null).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAs(tech, base + "/request-info", Map.of("comment", "Which SSID do you see in the room?"))
                .andExpect(jsonPath("$.status").value("PENDING_INFO")).andExpect(jsonPath("$.sla.paused").value(true));
        postAs(EMPLOYEE, base + "/provide-info", Map.of("comment", "Only the guest SSID is visible."))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        postAs(tech, base + "/notes", Map.of("content", "Access point AP-2B is unresponsive", "internal", true))
                .andExpect(status().isOk());
        postAs(tech, base + "/resolve", Map.of("summary", "Power-cycled and re-provisioned access point AP-2B",
                "rootCause", "Access point firmware hang")).andExpect(jsonPath("$.status").value("RESOLVED"));

        // Internal notes are hidden from the reporter
        JsonNode employeeView = read(getAs(EMPLOYEE, base));
        assertThat(employeeView.path("notes").findValuesAsText("content")).doesNotContain("Access point AP-2B is unresponsive");
        assertThat(employeeView.path("allowedActions").toString()).contains("VERIFY", "REOPEN");

        postAs(EMPLOYEE, base + "/verify", null).andExpect(jsonPath("$.status").value("VERIFIED"));
        postAs(EMPLOYEE, base + "/feedback", Map.of("rating", 5, "comment", "Fast fix")).andExpect(jsonPath("$.feedback.rating").value(5));
        postAs(tech, base + "/close", Map.of("notes", "Confirmed working")).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.sla.status").value("MET"));

        JsonNode inbox = read(getAs(EMPLOYEE, "/api/notifications?size=50"));
        assertThat(inbox.path("content").findValuesAsText("eventType")).contains("INFO_REQUESTED", "INCIDENT_RESOLVED");
    }

    @Test
    @DisplayName("TC-17 Workflow 3: multiple related reports -> duplicates detected -> incidents correlated")
    void workflow3DuplicatesAndCorrelation() throws Exception {
        JsonNode first = read(postAs(KARAN, "/api/incidents", report("Payment application unavailable",
                "The payment application is unavailable, customers see a maintenance page.", "PAYMENT", "HIGH", "HIGH", 200))
                .andExpect(status().isCreated()));
        JsonNode second = read(postAs(DIVYA, "/api/incidents", report("Payment transaction failing",
                "Every payment transaction is failing with a gateway error.", "PAYMENT", "HIGH", "HIGH", 150))
                .andExpect(status().isCreated()));
        JsonNode third = read(postAs(NIKHIL, "/api/incidents", report("Unable to make payment",
                "I am unable to make a payment for an order, it fails every time.", "PAYMENT", "MEDIUM", "HIGH", 1))
                .andExpect(status().isCreated()));

        assertThat(second.path("suspectedDuplicateOf").path("code").asText()).isNotBlank();
        assertThat(third.path("suspectedDuplicateOf").path("code").asText()).isNotBlank();

        JsonNode clusters = read(getAs(ADMIN, "/api/intelligence/correlations").andExpect(status().isOk()));
        boolean correlated = false;
        for (JsonNode c : clusters) {
            String all = c.toString();
            if (all.contains(first.path("number").asText()) && all.contains(third.path("number").asText())
                    && c.path("majorIncident").asBoolean()) correlated = true;
        }
        assertThat(correlated).as("payment incidents should be in one major-incident cluster").isTrue();

        // Merge: close the third as a duplicate of the first
        postAs(LEAD, "/api/incidents/" + third.path("id").asLong() + "/duplicate",
                Map.of("parentIncidentId", first.path("id").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closureCode").value("DUPLICATE"));
    }

    @Test
    @DisplayName("TC-18 Duplicate incident: the same reporter re-submitting is blocked unless forced")
    void sameReporterDuplicateBlocked() throws Exception {
        Map<String, Object> body = new HashMap<>(report("Monitor flickering on desk 42",
                "My monitor on desk 42 keeps flickering every few seconds.", "END_USER", "LOW", "LOW", 1));
        postAs(DIVYA, "/api/incidents", body).andExpect(status().isCreated());
        postAs(DIVYA, "/api/incidents", body).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_INCIDENT"))
                .andExpect(jsonPath("$.existingIncidents").isArray());
        body.put("force", true);
        postAs(DIVYA, "/api/incidents", body).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("TC-19 Authorization: other employees and unassigned technicians are blocked")
    void objectLevelAuthorization() throws Exception {
        JsonNode created = read(postAs(EMPLOYEE, "/api/incidents", report("Keyboard keys sticking badly",
                "Several keys on my keyboard stick and repeat characters.", "END_USER", "LOW", "MEDIUM", 1)));
        long id = created.path("id").asLong();
        getAs(KARAN, "/api/incidents/" + id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_OPERATION"));
        postAs(EMPLOYEE, "/api/incidents/" + id + "/acknowledge", null).andExpect(status().isForbidden());

        String assignee = created.path("assignedTo").path("userCode").asText();
        String outsider = TECH_EMAILS.entrySet().stream()
                .filter(e -> e.getKey().startsWith("T") && !e.getKey().equals(assignee))
                .findFirst().orElseThrow().getValue();
        postAs(outsider, "/api/incidents/" + id + "/acknowledge", null).andExpect(status().isForbidden());

        // Employees only ever see their own incidents in search, whatever filters they send
        JsonNode page = read(getAs(EMPLOYEE, "/api/incidents?size=100&reporterId=999"));
        page.path("content").forEach(i -> assertThat(i.path("reporter").path("userCode").asText()).isEqualTo("E101"));
    }

    @Test
    @DisplayName("TC-20 Invalid state transition via API returns 409 with the current state")
    void invalidTransition() throws Exception {
        JsonNode created = read(postAs(EMPLOYEE, "/api/incidents", report("Docking station not charging laptop",
                "The docking station no longer charges my laptop when connected.", "END_USER", "LOW", "LOW", 1)));
        postAs(EMPLOYEE, "/api/incidents/" + created.path("id").asLong() + "/verify", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATE_TRANSITION"))
                .andExpect(jsonPath("$.attemptedAction").value("VERIFY"));
    }

    @Test
    @DisplayName("TC-21 Invalid input and missing resources: validation errors and SERVICE_NOT_FOUND")
    void invalidInput() throws Exception {
        postAs(EMPLOYEE, "/api/incidents", Map.of("title", "Hi", "description", "short", "impact", "HUGE"))
                .andExpect(status().isBadRequest());
        postAs(EMPLOYEE, "/api/incidents", Map.of("title", "Valid title here", "description", "A valid long description",
                        "serviceId", 99999, "impact", "LOW", "urgency", "LOW"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SERVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("TC-22 Workload exceeded: manual assignment beyond a technician's capacity is refused")
    void workloadExceeded() throws Exception {
        JsonNode tech = read(postAs(ADMIN, "/api/admin/users", Map.of("role", "TECHNICIAN", "fullName", "Capacity Test",
                "email", "capacity.test@itsm.local", "password", "Secret123", "maxConcurrentIncidents", 1,
                "experienceYears", 1)).andExpect(status().isCreated()));
        long techId = tech.path("id").asLong();
        long a = read(postAs(KARAN, "/api/incidents", report("Mouse scroll wheel broken",
                "The scroll wheel on my mouse stopped working entirely.", "END_USER", "LOW", "LOW", 1))).path("id").asLong();
        long b = read(postAs(NIKHIL, "/api/incidents", report("Headset microphone not detected",
                "My USB headset microphone is not detected by the laptop.", "END_USER", "LOW", "LOW", 1))).path("id").asLong();

        postAs(ADMIN, "/api/incidents/" + a + "/assign", Map.of("technicianId", techId)).andExpect(status().isOk());
        postAs(ADMIN, "/api/incidents/" + b + "/assign", Map.of("technicianId", techId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("WORKLOAD_EXCEEDED"));
    }

    @Test
    @DisplayName("TC-23 SLA monitoring detects violations on overdue incidents and escalates them")
    void slaMonitoring() throws Exception {
        postAs(ADMIN, "/api/admin/sla/scan", null).andExpect(status().isOk()).andExpect(jsonPath("$.scanned").isNumber());
        JsonNode breached = read(getAs(ADMIN, "/api/incidents?slaStatus=BREACHED&size=100"));
        assertThat(breached.path("totalElements").asLong()).isPositive();
        JsonNode escalated = read(getAs(ADMIN, "/api/incidents?escalated=true&size=100"));
        assertThat(escalated.path("totalElements").asLong()).isPositive();
        getAs(ADMIN, "/api/intelligence/sla-risks").andExpect(status().isOk());
    }

    @Test
    @DisplayName("TC-24 Search with multiple filters returns only matching incidents")
    void searchFilters() throws Exception {
        JsonNode page = read(getAs(ADMIN, "/api/incidents?status=CLOSED&priority=P2&priority=P3&serviceId="
                + serviceId("EMAIL") + "&size=100").andExpect(status().isOk()));
        assertThat(page.path("content").size()).isPositive();
        page.path("content").forEach(i -> {
            assertThat(i.path("status").asText()).isEqualTo("CLOSED");
            assertThat(i.path("priority").asText()).isIn("P2", "P3");
            assertThat(i.path("service").path("code").asText()).isEqualTo("EMAIL");
        });
        getAs(ADMIN, "/api/incidents?sort=nonExistingField").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("TC-25 Strategy can be switched at runtime by an administrator; unknown keys are rejected")
    void switchStrategy() throws Exception {
        putAs(ADMIN, "/api/admin/settings/strategies", Map.of("priorityStrategy", "weighted",
                "assignmentStrategy", "round-robin", "autoAssign", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeAssignmentStrategy").value("round-robin"));
        putAs(ADMIN, "/api/admin/settings/strategies", Map.of("priorityStrategy", "matrix",
                "assignmentStrategy", "magic", "autoAssign", true))
                .andExpect(status().isBadRequest());
        putAs(ADMIN, "/api/admin/settings/strategies", Map.of("priorityStrategy", "matrix",
                "assignmentStrategy", "smart", "autoAssign", true)).andExpect(status().isOk());
        putAs(LEAD, "/api/admin/settings/strategies", Map.of("priorityStrategy", "matrix",
                "assignmentStrategy", "smart", "autoAssign", true)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Dashboard aggregates incidents, workload, SLA compliance and trends")
    void dashboard() throws Exception {
        getAs(ADMIN, "/api/reports/dashboard").andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.totalIncidents").isNumber())
                .andExpect(jsonPath("$.technicianWorkload").isArray())
                .andExpect(jsonPath("$.trend.length()").value(14));
    }
}
