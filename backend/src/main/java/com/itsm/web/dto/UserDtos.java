package com.itsm.web.dto;

import com.itsm.domain.user.AvailabilityStatus;
import com.itsm.domain.user.Role;
import com.itsm.web.dto.CommonDtos.Ref;
import com.itsm.web.dto.CommonDtos.UserRef;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Authentication and user management DTOs. Password hashes are never part of any response. */
public final class UserDtos {

    private UserDtos() {
    }

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain a letter and a digit";
    public static final String PHONE_RULE = "^$|^\\+?[0-9 ]{8,18}$";

    // ---------------------------------------------------------------- auth

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            @Size(max = 80) String department,
            @Pattern(regexp = PHONE_RULE, message = "Invalid phone number") String phone,
            @Size(max = 12) String verificationCode) {}

    public record VerificationCodeRequest(@NotBlank @Email @Size(max = 160) String email) {}

    public record ForgotPasswordRequest(@NotBlank @Email @Size(max = 160) String email) {}

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {}

    public record AuthResponse(String token, Instant expiresAt, UserDetail user) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @Size(max = 80) String department,
            @Pattern(regexp = PHONE_RULE, message = "Invalid phone number") String phone,
            boolean emailNotifications,
            boolean smsNotifications) {}

    // ---------------------------------------------------------------- responses

    public record TechnicianProfile(
            int experienceYears,
            int maxConcurrentIncidents,
            AvailabilityStatus availability,
            List<Ref> skills,
            List<Ref> serviceExpertise,
            UserRef teamLead,
            int activeIncidents) {}

    public record UserDetail(
            Long id,
            String userCode,
            String fullName,
            String email,
            Role role,
            String department,
            String phone,
            boolean active,
            Instant lastLoginAt,
            Instant createdAt,
            boolean emailNotifications,
            boolean smsNotifications,
            Set<String> permissions,
            TechnicianProfile technician) {}

    // ---------------------------------------------------------------- admin requests

    public record CreateUserRequest(
            @NotNull Role role,
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password,
            @Size(max = 80) String department,
            @Pattern(regexp = PHONE_RULE, message = "Invalid phone number") String phone,
            @Min(0) @Max(50) Integer experienceYears,
            @Min(1) @Max(50) Integer maxConcurrentIncidents,
            Set<Long> skillCategoryIds,
            Set<Long> serviceIds,
            Long teamLeadId) {}

    public record UpdateUserRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @Size(max = 80) String department,
            @Pattern(regexp = PHONE_RULE, message = "Invalid phone number") String phone,
            @Min(0) @Max(50) Integer experienceYears,
            @Min(1) @Max(50) Integer maxConcurrentIncidents,
            AvailabilityStatus availability,
            Set<Long> skillCategoryIds,
            Set<Long> serviceIds,
            Long teamLeadId) {}

    public record AvailabilityRequest(@NotNull AvailabilityStatus availability) {}

    /** Technician with live workload, for assignment pickers and the workload board. */
    public record TechnicianWorkload(
            UserRef technician,
            AvailabilityStatus availability,
            int activeIncidents,
            int maxConcurrentIncidents,
            double loadPercent,
            List<String> skills,
            int experienceYears,
            UserRef teamLead) {}
}
