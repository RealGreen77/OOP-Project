package com.itsm.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Authentication, password protection, lockout and role-based authorization. */
class AuthApiTest extends ApiTestBase {

    @Test
    @DisplayName("TC-11 Valid login returns a JWT and the user's role and permissions")
    void loginSuccess() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", LEAD, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.role").value("TEAM_LEAD"))
                .andExpect(jsonPath("$.user.permissions", hasItem("ASSIGN_INCIDENT")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("TC-12 Invalid login is rejected with a generic message; repeated failures lock the account")
    void invalidLoginAndLockout() throws Exception {
        String body = json.writeValueAsString(Map.of("email", "isha.agarwal@itsm.local", "password", "WrongPass1"));
        for (int attempt = 1; attempt <= 4; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
        // Even the correct password is refused while locked
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "isha.agarwal@itsm.local", "password", PASSWORD))))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("TC-13 Invalid user input: weak password and malformed e-mail fail validation with field errors")
    void registrationValidation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "New User", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    @DisplayName("Registration creates an EMPLOYEE; duplicate e-mails are rejected")
    void registration() throws Exception {
        var body = Map.of("fullName", "Test Person", "email", "test.person@itsm.local", "password", "Secret123",
                "department", "QA");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.user.userCode").isNotEmpty());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("TC-14 Unauthorized operations: no token -> 401, employee on admin API -> 403")
    void authorization() throws Exception {
        mvc.perform(get("/api/incidents")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/incidents").header("Authorization", "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
        getAs(EMPLOYEE, "/api/admin/users").andExpect(status().isForbidden());
        getAs(EMPLOYEE, "/api/reports/dashboard").andExpect(status().isForbidden());
        getAs(ADMIN, "/api/admin/users").andExpect(status().isOk());
    }
}
