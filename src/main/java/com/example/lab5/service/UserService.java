package com.example.lab5.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab5.exception.ConflictException;
import com.example.lab5.exception.NotFoundException;
import com.example.lab5.model.User;
import com.example.lab5.repository.UserRepository;
import com.example.lab5.repository.UserSessionRepository;

@Service
@Transactional
public class UserService {

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository users, UserSessionRepository sessions, PasswordEncoder passwordEncoder,
                       Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return users.findAll(Sort.by("id"));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    public User register(String username, String email, String rawPassword) {
        PasswordPolicy.require(username, rawPassword);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("Username '" + username + "' is already taken");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("E-mail '" + email + "' is already taken");
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(User.Role.USER);
        user.setStatus(User.Status.ACTIVE);
        user.setCreatedAt(Instant.now(clock));
        return users.save(user);
    }

    public User update(Long id, String username, String email) {
        User user = findById(id);
        if (username != null) {
            if (users.existsByUsernameIgnoreCaseAndIdNot(username, id)) {
                throw new ConflictException("Username '" + username + "' is already taken");
            }
            user.setUsername(username);
        }
        if (email != null) {
            if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
                throw new ConflictException("E-mail '" + email + "' is already taken");
            }
            user.setEmail(email);
        }
        return user;
    }

    public User block(Long actorId, Long id) {
        if (actorId.equals(id)) {
            throw new ConflictException("You cannot block your own account");
        }
        User user = findById(id);
        if (user.getStatus() == User.Status.BLOCKED) {
            throw new ConflictException("User " + id + " is already blocked");
        }
        user.setStatus(User.Status.BLOCKED);
        return user;
    }

    public User unblock(Long id) {
        User user = findById(id);
        boolean temporarilyLocked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now(clock));
        if (user.getStatus() == User.Status.ACTIVE && !temporarilyLocked) {
            throw new ConflictException("User " + id + " is not blocked");
        }
        user.setStatus(User.Status.ACTIVE);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        return user;
    }

    public User changeRole(Long actorId, Long id, User.Role role) {
        if (actorId.equals(id)) {
            throw new ConflictException("You cannot change your own role");
        }
        User user = findById(id);
        if (user.getRole() == role) {
            throw new ConflictException("User " + id + " already has role " + role);
        }
        user.setRole(role);
        return user;
    }

    public void delete(Long actorId, Long id) {
        if (actorId.equals(id)) {
            throw new ConflictException("You cannot delete your own account");
        }
        User user = findById(id);
        sessions.deleteAll(sessions.findAllByUsernameIgnoreCase(user.getUsername()));
        users.delete(user);
    }
}
