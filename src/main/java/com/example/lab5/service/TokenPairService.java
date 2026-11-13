package com.example.lab5.service;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab5.model.SessionStatus;
import com.example.lab5.model.User;
import com.example.lab5.model.UserSession;
import com.example.lab5.repository.UserRepository;
import com.example.lab5.repository.UserSessionRepository;
import com.example.lab5.security.AppUserDetails;
import com.example.lab5.security.InvalidTokenException;
import com.example.lab5.security.JwtService;

@Service
public class TokenPairService {

    public record TokenPair(String accessToken, String refreshToken) {}

    private static final Logger log = LoggerFactory.getLogger(TokenPairService.class);

    private final JwtService jwtService;
    private final UserSessionRepository sessions;
    private final UserRepository users;
    private final Clock clock;

    public TokenPairService(JwtService jwtService, UserSessionRepository sessions, UserRepository users,
                            Clock clock) {
        this.jwtService = jwtService;
        this.sessions = sessions;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public TokenPair issue(String username, String deviceId) {
        return createSession(username, deviceId, Instant.now(clock));
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public TokenPair refresh(String refreshToken) {
        Jwt claims = jwtService.parseRefreshToken(refreshToken);
        Instant now = Instant.now(clock);

        UserSession session = sessions.findByRefreshTokenForUpdate(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("Unknown refresh session"));
        boolean notExpired = session.getRefreshTokenExpiry().isAfter(now);

        if (session.getStatus() == SessionStatus.REVOKED) {
            throw new InvalidTokenException("Refresh session is revoked");
        }
        if (session.getStatus() == SessionStatus.USED) {
            if (notExpired) {
                session.setStatus(SessionStatus.REVOKED);
                sessions.saveAndFlush(session);
                log.warn("Reuse of a refresh token detected, session {} revoked", session.getId());
            }
            throw new InvalidTokenException("Refresh token was already used");
        }
        if (!notExpired) {
            throw new InvalidTokenException("Refresh session is expired");
        }
        if (!session.getUsername().equals(claims.getSubject())) {
            throw new InvalidTokenException("Refresh token does not belong to the session owner");
        }
        User user = users.findByUsernameIgnoreCase(session.getUsername())
                .orElseThrow(() -> new InvalidTokenException("User does not exist"));
        if (!new AppUserDetails(user, now).isAccountNonLocked()) {
            throw new InvalidTokenException("User is not allowed to refresh tokens");
        }

        session.setStatus(SessionStatus.USED);
        sessions.saveAndFlush(session);
        return createSession(user.getUsername(), session.getDeviceId(), now);
    }

    private TokenPair createSession(String username, String deviceId, Instant now) {
        JwtService.IssuedToken access = jwtService.createAccessToken(username, now);
        JwtService.IssuedToken refresh = jwtService.createRefreshToken(username, now);

        UserSession session = new UserSession();
        session.setUsername(username);
        session.setDeviceId(deviceId);
        session.setAccessToken(access.value());
        session.setRefreshToken(refresh.value());
        session.setAccessTokenExpiry(access.expiresAt());
        session.setRefreshTokenExpiry(refresh.expiresAt());
        session.setStatus(SessionStatus.ACTIVE);
        sessions.saveAndFlush(session);
        return new TokenPair(access.value(), refresh.value());
    }
}
