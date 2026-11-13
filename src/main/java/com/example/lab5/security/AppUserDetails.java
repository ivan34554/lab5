package com.example.lab5.security;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.example.lab5.model.User;

public class AppUserDetails implements UserDetails, CredentialsContainer {

    private final Long id;
    private final String username;
    private String passwordHash;
    private final User.Role role;
    private final boolean accountNonLocked;
    private final boolean failureTrace;

    public AppUserDetails(User user, Instant now) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        boolean blocked = user.getStatus() == User.Status.BLOCKED;
        boolean temporarilyLocked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        this.accountNonLocked = !blocked && !temporarilyLocked;
        this.failureTrace = user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null;
    }

    public Long getId() {
        return id;
    }

    public User.Role getRole() {
        return role;
    }

    public boolean hasFailureTrace() {
        return failureTrace;
    }

    public boolean hasAuthority(String authority) {
        return role.authorities().contains(authority);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> result = new ArrayList<>();
        result.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        role.authorities().forEach(a -> result.add(new SimpleGrantedAuthority(a)));
        return result;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
