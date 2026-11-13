package com.example.lab5.model;

import java.time.Instant;
import java.util.Set;
import com.example.lab5.security.Authority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    public enum Role {
        USER(Authority.CONTENT_WRITE),
        MODERATOR(Authority.CONTENT_WRITE, Authority.PODCAST_MODERATE),
        ADMIN(Authority.CONTENT_WRITE, Authority.PODCAST_MODERATE, Authority.USER_MANAGE);

        private final Set<String> authorities;

        Role(String... authorities) {
            this.authorities = Set.of(authorities);
        }

        public Set<String> authorities() {
            return authorities;
        }
    }

    public enum Status { ACTIVE, BLOCKED }

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;
}
