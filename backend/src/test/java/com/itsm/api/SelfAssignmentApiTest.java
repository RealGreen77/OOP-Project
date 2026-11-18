package com.itsm.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BR-19: staff may report incidents (they are employees too), but nobody is ever assigned an incident
 * they reported - not by the strategy, not in recommendations, not manually.
 */
class SelfAssignmentApiTest extends ApiTestBase {

    @Test
    @DisplayName("TC-32 A technician can report an incident but is never assigned it, automatically or manually")
    void reporterIsNeverAssignee() throws Exception {
        // An idle technician skilled in every category: the strategy's natural first choice
        Set<Long> allCategories = new HashSet<>();
        read(getAs(ADMIN, "/api/catalog/categories")).forEach(c -> allCategories.add(c.path("id").asLong()));
        JsonNode tech = read(postAs(ADMIN, "/api/admin/users", Map.of("role", "TECHNICIAN", "fullName", "Reporting Tech",
                "email", "reporting.tech@itsm.local", "password", PASSWORD, "maxConcurrentIncidents", 10,
                "experienceYears", 20, "skillCategoryIds", allCategories)).andExpect(status().isCreated()));
        long techId = tech.path("id").asLong();
        String techEmail = "reporting.tech@itsm.local";

        // 1. Staff can report
        JsonNode created = read(postAs(techEmail, "/api/incidents?force=true", report("My docking station is dead",
                "The docking station on my own desk does not power on any more.", "END_USER", "HIGH", "HIGH", 1))
                .andExpect(status().isCreated()));
        long id = created.path("id").asLong();
        assertThat(created.path("reporter").path("id").asLong()).isEqualTo(techId);

        // 2. ...but the strategy never picks the reporter
        assertThat(created.path("assignedTo").path("id").asLong()).isNotEqualTo(techId);

        // 3. ...nor do the recommendations offered to a team lead
        JsonNode recs = read(getAs(LEAD, "/api/intelligence/incidents/" + id + "/recommendations"));
        recs.path("recommendations").forEach(r -> assertThat(r.path("technician").path("id").asLong()).isNotEqualTo(techId));

        // 4. ...and a manual (re)assignment to the reporter is refused
        postAs(ADMIN, "/api/incidents/" + id + "/assign", Map.of("technicianId", techId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SELF_ASSIGNMENT_NOT_ALLOWED"));
    }
}
