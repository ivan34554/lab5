package com.example.lab5.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.example.lab5.dto.AuthDto;
import com.example.lab5.dto.UserDto;
import com.example.lab5.security.CurrentUser;
import com.example.lab5.service.AuthService;
import com.example.lab5.service.TokenPairService.TokenPair;
import com.example.lab5.service.UserService;
import jakarta.validation.Valid;

@RestController
public class AuthController {

    private final UserService users;
    private final AuthService auth;

    public AuthController(UserService users, AuthService auth) {
        this.users = users;
        this.auth = auth;
    }

    @GetMapping("/api/auth/csrf")
    public AuthDto.CsrfResponse csrf(CsrfToken token) {
        return new AuthDto.CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthDto.TokenResponse register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        return toResponse(auth.register(request.username(), request.email(), request.password(),
                request.deviceId()));
    }

    @PostMapping("/auth/login")
    public AuthDto.TokenResponse login(@Valid @RequestBody AuthDto.LoginRequest request) {
        return toResponse(auth.login(request.username(), request.password(), request.deviceId()));
    }

    @PostMapping("/auth/refresh")
    public AuthDto.TokenResponse refresh(@Valid @RequestBody AuthDto.RefreshRequest request) {
        return toResponse(auth.refresh(request.refreshToken()));
    }

    @GetMapping("/api/auth/me")
    @PreAuthorize("isAuthenticated()")
    public UserDto.Response me() {
        return UserDto.Response.of(users.findById(CurrentUser.require().getId()));
    }

    private static AuthDto.TokenResponse toResponse(TokenPair pair) {
        return new AuthDto.TokenResponse(pair.accessToken(), pair.refreshToken());
    }
}
