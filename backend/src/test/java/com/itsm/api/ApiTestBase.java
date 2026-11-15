package com.itsm.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Boots the full application (seeded H2) and offers helpers for authenticated JSON calls. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiTestBase {

    protected static final String PASSWORD = "Password@123";
    protected static final String ADMIN = "admin@itsm.local";
    protected static final String LEAD = "lead@itsm.local";
    protected static final String EMPLOYEE = "employee@itsm.local";
    protected static final String KARAN = "karan.singh@itsm.local";
    protected static final String DIVYA = "divya.menon@itsm.local";
    protected static final String NIKHIL = "nikhil.kumar@itsm.local";

    /** Seeded technician user codes -> login e-mails. */
    protected static final Map<String, String> TECH_EMAILS = Map.of(
            "T201", "tech@itsm.local", "T202", "sneha.patel@itsm.local", "T203", "rahul.verma@itsm.local",
            "T204", "kavya.nair@itsm.local", "T205", "rohan.das@itsm.local", "T206", "meera.joshi@itsm.local",
            "L301", "lead.infra@itsm.local", "L302", LEAD);

    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;

    protected String token(String email) throws Exception {
        String cached = TOKENS.get(email);
        if (cached != null) return cached;
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(body).path("token").asText();
        if (token.isEmpty()) throw new IllegalStateException("Login failed for " + email + ": " + body);
        TOKENS.put(email, token);
        return token;
    }

    protected ResultActions postAs(String email, String url, Object body) throws Exception {
        var req = post(url).header("Authorization", "Bearer " + token(email)).contentType(MediaType.APPLICATION_JSON);
        if (body != null) req.content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    protected ResultActions putAs(String email, String url, Object body) throws Exception {
        return mvc.perform(put(url).header("Authorization", "Bearer " + token(email))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected ResultActions getAs(String email, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token(email)));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected long serviceId(String code) throws Exception {
        for (JsonNode s : read(getAs(EMPLOYEE, "/api/catalog/services"))) {
            if (s.path("code").asText().equals(code)) return s.path("id").asLong();
        }
        throw new IllegalArgumentException("No service " + code);
    }

    protected long categoryId(String code) throws Exception {
        for (JsonNode c : read(getAs(EMPLOYEE, "/api/catalog/categories"))) {
            if (c.path("code").asText().equals(code)) return c.path("id").asLong();
        }
        throw new IllegalArgumentException("No category " + code);
    }

    protected Map<String, Object> report(String title, String description, String serviceCode, String impact,
                                         String urgency, int users) throws Exception {
        return Map.of("title", title, "description", description, "serviceId", serviceId(serviceCode),
                "impact", impact, "urgency", urgency, "affectedUsers", users);
    }
}
