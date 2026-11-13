package com.example.lab5.dto;

import java.time.Instant;
import com.example.lab5.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class UserDto {
    private UserDto() {}

    public record UpdateRequest(
            @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "may contain only letters, digits, '_', '.' and '-'")
            String username,
            @Email @Size(min = 3, max = 100) String email) {}

    public record RoleRequest(@NotNull User.Role role) {}

    public record Response(Long id, String username, String email, User.Role role, User.Status status,
                           Instant createdAt) {
        public static Response of(User user) {
            return new Response(user.getId(), user.getUsername(), user.getEmail(), user.getRole(),
                    user.getStatus(), user.getCreatedAt());
        }
    }
}
