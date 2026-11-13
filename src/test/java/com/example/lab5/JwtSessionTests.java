package com.example.lab5;

import static com.example.lab5.TestSupport.PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.example.lab5.model.SessionStatus;
import com.example.lab5.model.User;
import com.example.lab5.model.UserSession;
import com.example.lab5.repository.UserRepository;
import com.example.lab5.repository.UserSessionRepository;
import com.example.lab5.security.JwtProperties;
import com.example.lab5.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest
@AutoConfigureMockMvc
class JwtSessionTests {

    private record Pair(String access, String refresh) {}

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired UserSessionRepository sessions;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtProperties properties;
    @MockitoSpyBean JwtService jwtService;
    @Value("${app.jwt.secret}") String secret;

    private final List<String> usernames = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (String username : usernames) {
            sessions.deleteAll(sessions.findAllByUsernameIgnoreCase(username));
            users.findByUsernameIgnoreCase(username).ifPresent(users::delete);
        }
        usernames.clear();
    }

    private String newUser(User.Role role) {
        String username = TestSupport.uniqueName("jwt");
        usernames.add(username);
        TestSupport.createUser(users, encoder, username, role, User.Status.ACTIVE);
        return username;
    }

    private String newUser() {
        return newUser(User.Role.USER);
    }

    private ResultActions loginRequest(String username, String password, String deviceId) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\""
                + (deviceId == null ? "" : ",\"deviceId\":\"" + deviceId + "\"") + "}";
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Pair login(String username, String deviceId) throws Exception {
        return pairOf(loginRequest(username, PASSWORD, deviceId).andExpect(status().isOk()));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private Pair pairOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString();
        return new Pair(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
    }

    private ResultActions getMe(String accessToken) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + accessToken));
    }

    private void expectRejected(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    private UserSession sessionOf(String refreshToken) {
        return sessions.findByRefreshToken(refreshToken).orElseThrow();
    }

    private List<SessionStatus> statusesOf(String username) {
        return sessions.findAllByUsernameIgnoreCase(username).stream()
                .map(UserSession::getStatus).sorted().toList();
    }

    private static String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void loginIssuesSignedPairAndStoresActiveSession() throws Exception {
        String username = newUser();
        Instant before = Instant.now().minusSeconds(2);
        Pair pair = login(username, "phone-1");

        assertNotEquals(pair.access(), pair.refresh());
        Jwt access = jwtService.parseAccessToken(pair.access());
        Jwt refresh = jwtService.parseRefreshToken(pair.refresh());
        assertEquals(username, access.getSubject());
        assertEquals(username, refresh.getSubject());
        assertEquals("access", access.getClaimAsString("type"));
        assertEquals("refresh", refresh.getClaimAsString("type"));
        assertNotEquals(access.getId(), refresh.getId());
        assertEquals("HS256", access.getHeaders().get("alg"));
        assertEquals("HS256", refresh.getHeaders().get("alg"));
        assertEquals(properties.accessTtl(), Duration.between(access.getIssuedAt(), access.getExpiresAt()));
        assertEquals(properties.refreshTtl(), Duration.between(refresh.getIssuedAt(), refresh.getExpiresAt()));
        assertTrue(access.getIssuedAt().isAfter(before));

        UserSession session = sessionOf(pair.refresh());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertEquals(username, session.getUsername());
        assertEquals("phone-1", session.getDeviceId());
        assertEquals(pair.access(), session.getAccessToken());
        assertEquals(access.getExpiresAt(), session.getAccessTokenExpiry());
        assertEquals(refresh.getExpiresAt(), session.getRefreshTokenExpiry());
        assertEquals(1, sessions.findAllByUsernameIgnoreCase(username).size());
    }

    @Test
    void everyLoginCreatesItsOwnSessionWithNewTokenIds() throws Exception {
        String username = newUser();
        Pair first = login(username, null);
        Pair second = login(username, null);
        assertNotEquals(first.refresh(), second.refresh());
        assertNotEquals(jwtService.parseRefreshToken(first.refresh()).getId(),
                jwtService.parseRefreshToken(second.refresh()).getId());
        assertEquals(List.of(SessionStatus.ACTIVE, SessionStatus.ACTIVE), statusesOf(username));
    }

    @Test
    void invalidLoginsAreRejectedWithoutTokens() throws Exception {
        String username = newUser();
        expectRejected(loginRequest(username, "Wrong#Pass12345", null));
        expectRejected(loginRequest("nobody_" + System.nanoTime(), PASSWORD, null));
        loginRequest("", PASSWORD, null).andExpect(status().isBadRequest());
        loginRequest(username, "", null).andExpect(status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        assertEquals(0, sessions.findAllByUsernameIgnoreCase(username).size());
    }

    @Test
    void accessTokenOpensProtectedEndpointsInBearerHeader() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);

        getMe(pair.access()).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));

        mvc.perform(MockMvcRequestBuilders.post("/api/podcasts").header("Authorization", "Bearer " + pair.access())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Bearer podcast\"}"))
                .andExpect(status().isCreated());

        mvc.perform(MockMvcRequestBuilders.post("/api/podcasts").with(httpBasic(username, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Basic podcast\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void refreshRotatesPairAndMarksOldSessionUsed() throws Exception {
        String username = newUser();
        Pair first = login(username, "tablet-7");

        Pair second = pairOf(refresh(first.refresh()).andExpect(status().isOk()));
        assertNotEquals(first.access(), second.access());
        assertNotEquals(first.refresh(), second.refresh());

        UserSession oldSession = sessionOf(first.refresh());
        UserSession newSession = sessionOf(second.refresh());
        assertEquals(SessionStatus.USED, oldSession.getStatus());
        assertEquals(SessionStatus.ACTIVE, newSession.getStatus());
        assertEquals(username, newSession.getUsername());
        assertEquals("tablet-7", newSession.getDeviceId());
        assertEquals(second.access(), newSession.getAccessToken());
        assertEquals(jwtService.parseRefreshToken(second.refresh()).getExpiresAt(),
                newSession.getRefreshTokenExpiry());
        assertEquals(2, sessions.findAllByUsernameIgnoreCase(username).size());

        getMe(second.access()).andExpect(status().isOk());
    }

    @Test
    void reusedRefreshRevokesItsSessionAndIsRejected() throws Exception {
        String username = newUser();
        Pair first = login(username, null);
        Pair second = pairOf(refresh(first.refresh()).andExpect(status().isOk()));

        expectRejected(refresh(first.refresh()));
        assertEquals(SessionStatus.REVOKED, sessionOf(first.refresh()).getStatus());
        assertEquals(SessionStatus.ACTIVE, sessionOf(second.refresh()).getStatus());
        assertEquals(2, sessions.findAllByUsernameIgnoreCase(username).size());

        expectRejected(refresh(first.refresh()));
        assertEquals(SessionStatus.REVOKED, sessionOf(first.refresh()).getStatus());
        assertEquals(2, sessions.findAllByUsernameIgnoreCase(username).size());

        refresh(second.refresh()).andExpect(status().isOk());
    }

    @Test
    void onlyOneOfTwoConcurrentRefreshesSucceeds() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return refresh(pair.refresh()).andReturn().getResponse().getStatus();
            }));
        }
        ready.await();
        go.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertEquals(1, statuses.stream().filter(s -> s == 200).count());
        assertEquals(1, statuses.stream().filter(s -> s == 401).count());
        assertEquals(List.of(SessionStatus.ACTIVE, SessionStatus.REVOKED), statusesOf(username));
    }

    @Test
    void refreshNeedsNeitherAccessTokenNorCsrf() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").header("Authorization", "Bearer expired.garbage")
                .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"" + pair.refresh() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void refreshWithMissingOrBlankTokenIsBadRequest() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"  \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void tokenTypesCannotBeSwapped() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);

        expectRejected(refresh(pair.access()));
        getMe(pair.refresh()).andExpect(status().isUnauthorized());
        assertEquals(SessionStatus.ACTIVE, sessionOf(pair.refresh()).getStatus());
    }

    @Test
    void forgedAndMalformedTokensAreRejected() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);

        char[] chars = pair.refresh().toCharArray();
        int position = pair.refresh().lastIndexOf('.') + 6;
        chars[position] = chars[position] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        expectRejected(refresh(tampered));

        JwtService foreign = new JwtService(new JwtProperties("another-secret-value-0123456789-abcdef",
                Duration.ofMinutes(15), Duration.ofDays(7)));
        Instant now = Instant.now();
        expectRejected(refresh(foreign.createRefreshToken(username, now).value()));
        getMe(foreign.createAccessToken(username, now).value()).andExpect(status().isUnauthorized());

        long exp = now.plusSeconds(600).getEpochSecond();
        String unsigned = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "."
                + base64Url("{\"sub\":\"" + username + "\",\"jti\":\"1\",\"type\":\"refresh\",\"iat\":"
                + now.getEpochSecond() + ",\"exp\":" + exp + "}") + ".";
        expectRejected(refresh(unsigned));

        expectRejected(refresh("not-a-jwt"));
        getMe("not-a-jwt").andExpect(status().isUnauthorized());
        assertEquals(SessionStatus.ACTIVE, sessionOf(pair.refresh()).getStatus());
    }

    @Test
    void tokensWithoutRequiredClaimsAreRejected() throws Exception {
        String username = newUser();
        NimbusJwtEncoder signer = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(username)
                .expiresAt(Instant.now().plusSeconds(600)).build();
        String incomplete = signer.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        expectRejected(refresh(incomplete));
        getMe(incomplete).andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAndUnknownTokensAreRejected() throws Exception {
        String username = newUser();
        Instant past = Instant.now().minus(Duration.ofDays(30));

        JwtService.IssuedToken expiredAccess = jwtService.createAccessToken(username, past);
        getMe(expiredAccess.value()).andExpect(status().isUnauthorized());

        JwtService.IssuedToken expiredRefresh = jwtService.createRefreshToken(username, past);
        UserSession stale = new UserSession();
        stale.setUsername(username);
        stale.setAccessToken(expiredAccess.value());
        stale.setRefreshToken(expiredRefresh.value());
        stale.setAccessTokenExpiry(expiredAccess.expiresAt());
        stale.setRefreshTokenExpiry(expiredRefresh.expiresAt());
        stale.setStatus(SessionStatus.ACTIVE);
        sessions.saveAndFlush(stale);
        expectRejected(refresh(expiredRefresh.value()));
        assertEquals(SessionStatus.ACTIVE, sessionOf(expiredRefresh.value()).getStatus());

        expectRejected(refresh(jwtService.createRefreshToken(username, Instant.now()).value()));
    }

    @Test
    void blockedUserCannotUseOrRefreshTokens() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);

        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        user.setStatus(User.Status.BLOCKED);
        users.saveAndFlush(user);

        getMe(pair.access()).andExpect(status().isUnauthorized());
        expectRejected(refresh(pair.refresh()));
        expectRejected(loginRequest(username, PASSWORD, null));
        assertEquals(SessionStatus.ACTIVE, sessionOf(pair.refresh()).getStatus());
        assertEquals(1, sessions.findAllByUsernameIgnoreCase(username).size());
    }

    @Test
    void removedUserCannotRefresh() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);
        users.delete(users.findByUsernameIgnoreCase(username).orElseThrow());

        expectRejected(refresh(pair.refresh()));
        getMe(pair.access()).andExpect(status().isUnauthorized());
        assertEquals(SessionStatus.ACTIVE, sessionOf(pair.refresh()).getStatus());
    }

    @Test
    void deletingUserThroughApiRemovesItsSessions() throws Exception {
        String admin = newUser(User.Role.ADMIN);
        String victim = newUser();
        Pair adminPair = login(admin, null);
        login(victim, null);
        long victimId = users.findByUsernameIgnoreCase(victim).orElseThrow().getId();

        mvc.perform(MockMvcRequestBuilders.delete("/api/users/" + victimId)
                .header("Authorization", "Bearer " + adminPair.access()))
                .andExpect(status().isNoContent());
        assertEquals(0, sessions.findAllByUsernameIgnoreCase(victim).size());
        assertEquals(1, sessions.findAllByUsernameIgnoreCase(admin).size());
    }

    @Test
    void registrationReturnsPairAndStoresSession() throws Exception {
        String username = TestSupport.uniqueName("reg");
        usernames.add(username);
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"deviceId\":\"laptop\"}";

        Pair pair = pairOf(mvc.perform(MockMvcRequestBuilders.post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()));

        UserSession session = sessionOf(pair.refresh());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
        assertEquals(username, session.getUsername());
        assertEquals("laptop", session.getDeviceId());
        getMe(pair.access()).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));

        mvc.perform(MockMvcRequestBuilders.post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        assertEquals(1, sessions.findAllByUsernameIgnoreCase(username).size());
    }

    @Test
    void registrationIsRolledBackWhenSessionCannotBeStored() throws Exception {
        String username = TestSupport.uniqueName("rollback");
        usernames.add(username);
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"" + PASSWORD + "\"}";
        doThrow(new IllegalStateException("token generation failed")).when(jwtService).createAccessToken(any(), any());

        mvc.perform(MockMvcRequestBuilders.post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertTrue(users.findByUsernameIgnoreCase(username).isEmpty());
        assertEquals(0, sessions.findAllByUsernameIgnoreCase(username).size());
    }

    @Test
    void refreshIsRolledBackWhenNewPairCannotBeStored() throws Exception {
        String username = newUser();
        Pair pair = login(username, null);
        doThrow(new IllegalStateException("token generation failed")).when(jwtService).createRefreshToken(any(), any());

        refresh(pair.refresh()).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        assertEquals(SessionStatus.ACTIVE, sessionOf(pair.refresh()).getStatus());
        assertEquals(1, sessions.findAllByUsernameIgnoreCase(username).size());

        reset(jwtService);
        refresh(pair.refresh()).andExpect(status().isOk());
        assertEquals(List.of(SessionStatus.ACTIVE, SessionStatus.USED), statusesOf(username));
    }
}
