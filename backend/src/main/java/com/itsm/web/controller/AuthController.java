package com.itsm.web.controller;

import com.itsm.service.AuthService;
import com.itsm.web.dto.CommonDtos.MessageResponse;
import com.itsm.web.dto.UserDtos.AuthResponse;
import com.itsm.web.dto.UserDtos.ChangePasswordRequest;
import com.itsm.web.dto.UserDtos.LoginRequest;
import com.itsm.web.dto.UserDtos.RegisterRequest;
import com.itsm.web.dto.UserDtos.UpdateProfileRequest;
import com.itsm.web.dto.UserDtos.UserDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login and own profile")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Self-register as an employee")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in and receive a JWT")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Current user profile and permissions")
    public UserDetail me() {
        return authService.me();
    }

    @PutMapping("/me")
    @Operation(summary = "Update own profile and notification preferences")
    public UserDetail updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }

    @PostMapping("/me/password")
    @Operation(summary = "Change own password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return new MessageResponse("Password changed");
    }
}
