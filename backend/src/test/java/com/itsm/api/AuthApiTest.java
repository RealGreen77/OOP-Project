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
    @DisplayName("TC-26 Registration with e-mail verification: code e-mailed, account created as EMPLOYEE, duplicates rejected")
    void registration() throws Exception {
        String email = "test.person@example.org";
        requestCode(email).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.expiresInMinutes").value(10));
        String code = mailbox.latestCode(email);

        Map<String, Object> body = new java.util.HashMap<>(Map.of("fullName", "Test Person", "email", email,
                "password", "Secret123", "department", "QA", "verificationCode", code));
        register(body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.user.userCode").isNotEmpty());
        // The address is now taken: both a new code and a second registration are refused
        requestCode(email).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"));
        register(body).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("USER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("TC-27 Registration without a code, or with someone else's address, is refused")
    void registrationRequiresCode() throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("fullName", "Imposter", "email", "victim@example.org",
                "password", "Secret123"));
        register(body).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_REQUIRED"));
        body.put("verificationCode", "123456");
        register(body).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CODE_INVALID"));
    }

    @Test
    @DisplayName("TC-28 Wrong codes are counted: after 5 the code is dead even if correct; resend is rate-limited")
    void verificationAttemptsAndResend() throws Exception {
        String email = "guesser@example.org";
        requestCode(email).andExpect(status().isAccepted());
        String code = mailbox.latestCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";
        Map<String, Object> body = new java.util.HashMap<>(Map.of("fullName", "Guesser", "email", email,
                "password", "Secret123", "verificationCode", wrong));
        for (int i = 4; i >= 0; i--) {
            register(body).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("CODE_INVALID"));
        }
        body.put("verificationCode", code);
        register(body).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CODE_ATTEMPTS_EXCEEDED"));
        // A new code cannot be requested straight away
        requestCode(email).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("CODE_RECENTLY_SENT"));
        // ...and the refused request did not send another e-mail
        org.assertj.core.api.Assertions.assertThat(mailbox.inbox(email)).hasSize(1);
    }

    private org.springframework.test.web.servlet.ResultActions requestCode(String email) throws Exception {
        return mvc.perform(post("/api/auth/register/request-code").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email))));
    }

    private org.springframework.test.web.servlet.ResultActions register(Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
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
