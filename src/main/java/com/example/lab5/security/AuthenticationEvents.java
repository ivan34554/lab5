package com.example.lab5.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import com.example.lab5.service.LoginAttemptService;

@Component
public class AuthenticationEvents {

    private final LoginAttemptService attempts;

    public AuthenticationEvents(LoginAttemptService attempts) {
        this.attempts = attempts;
    }

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        attempts.recordFailure(event.getAuthentication().getName());
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof AppUserDetails details && details.hasFailureTrace()) {
            attempts.recordSuccess(details.getId());
        }
    }
}
