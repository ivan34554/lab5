package com.example.lab5.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab5.model.User;
import com.example.lab5.service.TokenPairService.TokenPair;

@Service
public class AuthService {

    private final AuthenticationConfiguration authenticationConfiguration;
    private final UserService users;
    private final TokenPairService tokens;

    public AuthService(AuthenticationConfiguration authenticationConfiguration, UserService users,
                       TokenPairService tokens) {
        this.authenticationConfiguration = authenticationConfiguration;
        this.users = users;
        this.tokens = tokens;
    }

    public TokenPair login(String username, String password, String deviceId) {
        Authentication authentication = authenticationManager()
                .authenticate(UsernamePasswordAuthenticationToken.unauthenticated(username, password));
        UserDetails principal = (UserDetails) authentication.getPrincipal();
        return tokens.issue(principal.getUsername(), deviceId);
    }

    @Transactional
    public TokenPair register(String username, String email, String password, String deviceId) {
        User user = users.register(username, email, password);
        return tokens.issue(user.getUsername(), deviceId);
    }

    public TokenPair refresh(String refreshToken) {
        return tokens.refresh(refreshToken);
    }

    private AuthenticationManager authenticationManager() {
        try {
            return authenticationConfiguration.getAuthenticationManager();
        } catch (Exception e) {
            throw new IllegalStateException("Authentication manager is not available", e);
        }
    }
}
