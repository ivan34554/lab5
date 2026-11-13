package com.example.lab5;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.example.lab5.model.User;
import com.example.lab5.repository.UserRepository;

final class TestSupport {
    private TestSupport() {}

    static final String PASSWORD = "Test#Pass12345";

    private static long counter;

    static synchronized String uniqueName(String prefix) {
        return prefix + System.nanoTime() + "_" + (++counter);
    }

    static User createUser(UserRepository users, PasswordEncoder encoder, String username, User.Role role,
                           User.Status status) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(Instant.now());
        return users.saveAndFlush(user);
    }

    static RequestPostProcessor as(String username) {
        return httpBasic(username, PASSWORD);
    }
}
