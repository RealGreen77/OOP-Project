package com.itsm.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forgot-password flow: e-mailed single-use link, no account enumeration, lockout cleared. */
class PasswordResetApiTest extends ApiTestBase {

    @Test
    @DisplayName("TC-29 Forgot password: reset link e-mailed, new password works, old one and the link stop working")
    void resetFlow() throws Exception {
        String email = registerUser("reset.me@example.org", "OldSecret1");

        forgot(email).andExpect(status().isAccepted());
        String token = mailbox.latestResetToken(email);
        assertThat(token).hasSizeGreaterThan(40);

        reset(token, "NewSecret2").andExpect(status().isNoContent());
        login(email, "NewSecret2").andExpect(status().isOk());
        login(email, "OldSecret1").andExpect(status().isUnauthorized());
        // Single use
        reset(token, "Another3x").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RESET_LINK_INVALID"));
    }

    @Test
    @DisplayName("TC-30 Forgot password never reveals whether an account exists, and bad tokens are refused")
    void noEnumerationAndInvalidToken() throws Exception {
        forgot("nobody.here@example.org").andExpect(status().isAccepted());
        assertThat(mailbox.inbox("nobody.here@example.org")).isEmpty();
        reset("not-a-real-token-value-at-all-0000000000000", "NewSecret2")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("RESET_LINK_INVALID"));
        // Weak new password fails validation before the token is even checked
        reset("anything", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    @DisplayName("TC-31 Reset clears a login lockout, rejects reusing the old password and rate-limits new links")
    void resetUnlocksAccount() throws Exception {
        String email = registerUser("locked.out@example.org", "Original1");
        for (int i = 0; i < 5; i++) login(email, "WrongPass9");
        login(email, "Original1").andExpect(status().isLocked());

        forgot(email).andExpect(status().isAccepted());
        forgot(email).andExpect(status().isAccepted()); // within a minute: silently not re-sent
        assertThat(mailbox.inbox(email).stream().filter(m -> m.subject().contains("Reset")).count()).isEqualTo(1);

        String token = mailbox.latestResetToken(email);
        reset(token, "Original1").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_REUSED"));
        reset(token, "Brandnew4").andExpect(status().isNoContent());
        login(email, "Brandnew4").andExpect(status().isOk());
    }

    private String registerUser(String email, String password) throws Exception {
        post("/api/auth/register/request-code", Map.of("email", email)).andExpect(status().isAccepted());
        post("/api/auth/register", Map.of("fullName", "Reset Tester", "email", email, "password", password,
                "verificationCode", mailbox.latestCode(email))).andExpect(status().isCreated());
        return email;
    }

    private ResultActions forgot(String email) throws Exception {
        return post("/api/auth/password/forgot", Map.of("email", email));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        return post("/api/auth/password/reset", Map.of("token", token, "newPassword", newPassword));
    }

    private ResultActions login(String email, String password) throws Exception {
        return post("/api/auth/login", Map.of("email", email, "password", password));
    }

    private ResultActions post(String url, Object body) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
}
