package com.example.lab5.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Component
public class JwtService {

    public static final String CLAIM_TYPE = "type";

    private static final int MIN_SECRET_BYTES = 32;

    public record IssuedToken(String value, Instant expiresAt) {}

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtService(JwtProperties properties) {
        if (properties.secret() == null
                || properties.secret().getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        SecretKey key = new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                new JwtTimestampValidator(Duration.ZERO), JwtService::checkRequiredClaims));
        this.properties = properties;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = nimbusDecoder;
    }

    public IssuedToken createAccessToken(String username, Instant issuedAt) {
        return create(username, issuedAt, TokenType.ACCESS, properties.accessTtl());
    }

    public IssuedToken createRefreshToken(String username, Instant issuedAt) {
        return create(username, issuedAt, TokenType.REFRESH, properties.refreshTtl());
    }

    public Jwt parseAccessToken(String token) {
        return parse(token, TokenType.ACCESS);
    }

    public Jwt parseRefreshToken(String token) {
        return parse(token, TokenType.REFRESH);
    }

    private IssuedToken create(String username, Instant issuedAt, TokenType type, Duration ttl) {
        Instant iat = issuedAt.truncatedTo(ChronoUnit.SECONDS);
        Instant exp = iat.plus(ttl).truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .id(UUID.randomUUID().toString())
                .issuedAt(iat)
                .expiresAt(exp)
                .claim(CLAIM_TYPE, type.claimValue())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(value, exp);
    }

    private Jwt parse(String token, TokenType expected) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Token is missing");
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            throw new InvalidTokenException("Token is invalid");
        }
        if (!expected.claimValue().equals(jwt.getClaimAsString(CLAIM_TYPE))) {
            throw new InvalidTokenException("Unexpected token type");
        }
        return jwt;
    }

    private static OAuth2TokenValidatorResult checkRequiredClaims(Jwt jwt) {
        boolean complete = jwt.getSubject() != null && !jwt.getSubject().isBlank()
                && jwt.getId() != null && !jwt.getId().isBlank()
                && jwt.getIssuedAt() != null
                && jwt.getExpiresAt() != null
                && jwt.getClaimAsString(CLAIM_TYPE) != null;
        if (complete) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Required claims are missing", null));
    }
}
