package com.example.lab5.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab5.model.User;
import com.example.lab5.repository.UserRepository;
import com.example.lab5.security.SecurityProperties;

@Service
@Transactional
public class LoginAttemptService {

    private final UserRepository users;
    private final SecurityProperties properties;
    private final Clock clock;

    public LoginAttemptService(UserRepository users, SecurityProperties properties, Clock clock) {
        this.users = users;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordFailure(String username) {
        users.findByUsernameIgnoreCase(username).ifPresent(user -> {
            Instant now = Instant.now(clock);
            if (user.getStatus() != User.Status.ACTIVE) {
                return;
            }
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                return;
            }
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= properties.maxFailedAttempts()) {
                user.setLockedUntil(now.plus(properties.lockDuration()));
                user.setFailedLoginAttempts(0);
            } else {
                user.setFailedLoginAttempts(attempts);
            }
        });
    }

    public void recordSuccess(Long userId) {
        users.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        });
    }
}
