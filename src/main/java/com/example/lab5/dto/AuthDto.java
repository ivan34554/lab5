package com.example.lab5.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDto {
    private AuthDto() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "may contain only letters, digits, '_', '.' and '-'")
            String username,
            @NotBlank @Email @Size(max = 100) String email,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 100) String deviceId) {}

    public record LoginRequest(
            @NotBlank @Size(max = 50) String username,
            @NotBlank @Size(max = 128) String password,
            @Size(max = 100) String deviceId) {}

    public record RefreshRequest(@NotBlank @Size(max = 4096) String refreshToken) {}

    public record TokenResponse(String accessToken, String refreshToken) {}

    public record CsrfResponse(String headerName, String parameterName, String token) {}
}
