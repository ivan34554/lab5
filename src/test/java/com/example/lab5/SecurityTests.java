package com.example.lab5;

import static com.example.lab5.TestSupport.PASSWORD;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import com.example.lab5.model.User;
import com.example.lab5.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private User admin;
    private User moderator;
    private User alice;
    private User bob;

    @BeforeEach
    void createActors() {
        admin = actor("admin", User.Role.ADMIN);
        moderator = actor("moderator", User.Role.MODERATOR);
        alice = actor("alice", User.Role.USER);
        bob = actor("bob", User.Role.USER);
    }

    private User actor(String prefix, User.Role role) {
        return TestSupport.createUser(users, encoder, TestSupport.uniqueName(prefix), role, User.Status.ACTIVE);
    }

    private ResultActions get(User actor, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(url).with(TestSupport.as(actor.getUsername())));
    }

    private ResultActions post(User actor, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).with(TestSupport.as(actor.getUsername())).with(csrf()));
    }

    private ResultActions post(User actor, String url, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).with(TestSupport.as(actor.getUsername())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions patch(User actor, String url, String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.patch(url).with(TestSupport.as(actor.getUsername())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions delete(User actor, String url) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.delete(url).with(TestSupport.as(actor.getUsername())).with(csrf()));
    }

    private long createAs(User actor, String url, String body) throws Exception {
        String response = post(actor, url, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private String registerBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\""
                + password + "\"}";
    }

    private long podcastOf(User owner) throws Exception {
        return createAs(owner, "/api/podcasts", "{\"title\":\"Podcast\"}");
    }

    private long episodeOf(User owner, long podcastId) throws Exception {
        return createAs(owner, "/api/episodes",
                "{\"podcastId\":" + podcastId + ",\"title\":\"Episode\",\"durationSeconds\":600}");
    }

    private long audioOf(User owner, long episodeId, String path, boolean main) throws Exception {
        return createAs(owner, "/api/audio-files", "{\"episodeId\":" + episodeId + ",\"path\":\"" + path
                + "\",\"format\":\"MP3\",\"sizeBytes\":1024,\"main\":" + main + "}");
    }

    @Test
    void anonymousRequestsAreRejectedWith401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/podcasts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(MockMvcRequestBuilders.get("/api/users")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/api/podcasts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericAnswer() throws Exception {
        String message = "Authentication required or credentials are invalid";
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").with(httpBasic(alice.getUsername(), "Wrong#Pass12345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(content().string(not(containsString(alice.getUsername()))));
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").with(httpBasic("nobody_" + System.nanoTime(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void stateChangingRequestsWithoutCsrfTokenAreForbidden() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/podcasts").with(TestSupport.as(alice.getUsername()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(MockMvcRequestBuilders.delete("/api/podcasts/1").with(TestSupport.as(alice.getUsername())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        get(alice, "/api/podcasts").andExpect(status().isOk());
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void csrfTokenCanBeFetchedAndUsed() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN")))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        assertNotNull(token);
        Cookie cookie = new Cookie("XSRF-TOKEN", token);
        String body = registerBody(TestSupport.uniqueName("csrf"), PASSWORD);

        mvc.perform(MockMvcRequestBuilders.post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mvc.perform(MockMvcRequestBuilders.post("/auth/register").cookie(cookie)
                .header("X-XSRF-TOKEN", "forged-token").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mvc.perform(MockMvcRequestBuilders.post("/auth/register").cookie(cookie)
                .header("X-XSRF-TOKEN", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void registrationCreatesOrdinaryUserAndStoresOnlyBcryptHash() throws Exception {
        String username = TestSupport.uniqueName("reg");
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\",\"status\":\"ACTIVE\"}";
        mvc.perform(MockMvcRequestBuilders.post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = users.findByUsernameIgnoreCase(username).orElseThrow();
        assertEquals(User.Role.USER, saved.getRole());
        assertEquals(User.Status.ACTIVE, saved.getStatus());
        assertNotEquals(PASSWORD, saved.getPasswordHash());
        assertTrue(saved.getPasswordHash().startsWith("$2"));
        assertTrue(encoder.matches(PASSWORD, saved.getPasswordHash()));

        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").with(httpBasic(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void weakPasswordsAreRejected() throws Exception {
        String longPassword = "Aa1!" + "a".repeat(80);
        String[] weak = { "Sh0rt!a", "alllowercase123!", "ALLUPPERCASE123!", "NoDigitsHere!!", "NoSpecialChars123",
                "Password123!", longPassword };
        for (String password : weak) {
            mvc.perform(MockMvcRequestBuilders.post("/auth/register").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody(TestSupport.uniqueName("weak"), password)))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().string(not(containsString(password))));
        }
        String username = TestSupport.uniqueName("self");
        mvc.perform(MockMvcRequestBuilders.post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(username, username + "Aa1!xyz")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void usernameAndEmailMustBeUnique() throws Exception {
        String username = TestSupport.uniqueName("dup");
        mvc.perform(MockMvcRequestBuilders.post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(registerBody(username, PASSWORD)))
                .andExpect(status().isCreated());
        mvc.perform(MockMvcRequestBuilders.post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(registerBody(username.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict());
        String sameEmail = "{\"username\":\"" + TestSupport.uniqueName("other") + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(MockMvcRequestBuilders.post("/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(sameEmail))
                .andExpect(status().isConflict());
    }

    @Test
    void blockedUserCannotAuthenticateUntilUnblocked() throws Exception {
        post(admin, "/api/users/" + bob.getId() + "/block")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));
        post(admin, "/api/users/" + bob.getId() + "/block").andExpect(status().isConflict());

        get(bob, "/api/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required or credentials are invalid"));

        post(admin, "/api/users/" + bob.getId() + "/unblock")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        post(admin, "/api/users/" + bob.getId() + "/unblock").andExpect(status().isConflict());
        get(bob, "/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void repeatedFailedLoginsLockTheAccountTemporarily() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                    .with(httpBasic(alice.getUsername(), "Wrong#Pass12345")))
                    .andExpect(status().isUnauthorized());
        }
        assertNotNull(users.findByUsernameIgnoreCase(alice.getUsername()).orElseThrow().getLockedUntil());

        get(alice, "/api/auth/me").andExpect(status().isUnauthorized());

        post(admin, "/api/users/" + alice.getId() + "/unblock").andExpect(status().isOk());
        get(alice, "/api/auth/me").andExpect(status().isOk());
    }

    @Test
    void successfulLoginResetsFailedAttempts() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                    .with(httpBasic(alice.getUsername(), "Wrong#Pass12345")))
                    .andExpect(status().isUnauthorized());
        }
        assertEquals(3, users.findByUsernameIgnoreCase(alice.getUsername()).orElseThrow().getFailedLoginAttempts());
        get(alice, "/api/auth/me").andExpect(status().isOk());
        assertEquals(0, users.findByUsernameIgnoreCase(alice.getUsername()).orElseThrow().getFailedLoginAttempts());
    }

    @Test
    void userManagementIsLimitedToAdministrators() throws Exception {
        get(alice, "/api/users").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        get(moderator, "/api/users").andExpect(status().isForbidden());
        get(admin, "/api/users").andExpect(status().isOk());

        post(alice, "/api/users/" + bob.getId() + "/block").andExpect(status().isForbidden());
        post(alice, "/api/users/" + bob.getId() + "/role", "{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
        post(alice, "/api/users/" + alice.getId() + "/role", "{\"role\":\"ADMIN\"}").andExpect(status().isForbidden());
        delete(alice, "/api/users/" + bob.getId()).andExpect(status().isForbidden());
        post(moderator, "/api/users/" + bob.getId() + "/block").andExpect(status().isForbidden());
    }

    @Test
    void usersSeeOnlyTheirOwnProfile() throws Exception {
        get(alice, "/api/users/" + alice.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(alice.getEmail()));
        get(alice, "/api/users/" + bob.getId()).andExpect(status().isForbidden());
        get(admin, "/api/users/" + bob.getId()).andExpect(status().isOk());

        String renamed = TestSupport.uniqueName("renamed");
        patch(alice, "/api/users/" + alice.getId(), "{\"username\":\"" + renamed + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(renamed));
        patch(alice, "/api/users/" + bob.getId(), "{\"username\":\"hijacked\"}").andExpect(status().isForbidden());
    }

    @Test
    void roleChangesFollowBusinessRules() throws Exception {
        post(admin, "/api/users/" + bob.getId() + "/role", "{\"role\":\"MODERATOR\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MODERATOR"));
        post(admin, "/api/users/" + bob.getId() + "/role", "{\"role\":\"MODERATOR\"}")
                .andExpect(status().isConflict());
        post(admin, "/api/users/" + admin.getId() + "/role", "{\"role\":\"USER\"}")
                .andExpect(status().isConflict());
        post(admin, "/api/users/" + bob.getId() + "/role", "{\"role\":\"SUPERUSER\"}")
                .andExpect(status().isBadRequest());
        post(admin, "/api/users/" + bob.getId() + "/role", "{}").andExpect(status().isBadRequest());
        post(admin, "/api/users/" + admin.getId() + "/block").andExpect(status().isConflict());
        delete(admin, "/api/users/" + admin.getId()).andExpect(status().isConflict());
        get(admin, "/api/users/999999999").andExpect(status().isNotFound());
        delete(admin, "/api/users/" + bob.getId()).andExpect(status().isNoContent());
    }

    @Test
    void podcastsCanBeChangedOnlyByTheirAuthorOrAnAdministrator() throws Exception {
        long podcast = podcastOf(alice);
        get(alice, "/api/podcasts/" + podcast).andExpect(jsonPath("$.authorId").value(alice.getId()));

        post(alice, "/api/podcasts", "{\"title\":\"Fake\",\"authorId\":" + bob.getId() + "}")
                .andExpect(status().isForbidden());
        get(bob, "/api/podcasts/" + podcast).andExpect(status().isOk());
        patch(bob, "/api/podcasts/" + podcast, "{\"title\":\"Hijacked\"}").andExpect(status().isForbidden());
        delete(bob, "/api/podcasts/" + podcast).andExpect(status().isForbidden());
        post(bob, "/api/podcasts/" + podcast + "/archive").andExpect(status().isForbidden());

        patch(alice, "/api/podcasts/" + podcast, "{\"authorId\":" + bob.getId() + "}")
                .andExpect(status().isForbidden());
        patch(alice, "/api/podcasts/" + podcast, "{\"title\":\"Renamed\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Renamed"));
        patch(moderator, "/api/podcasts/" + podcast, "{\"title\":\"Moderated\"}").andExpect(status().isForbidden());
        delete(moderator, "/api/podcasts/" + podcast).andExpect(status().isForbidden());

        patch(admin, "/api/podcasts/" + podcast, "{\"authorId\":" + bob.getId() + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorId").value(bob.getId()));
        patch(alice, "/api/podcasts/" + podcast, "{\"title\":\"Lost\"}").andExpect(status().isForbidden());
        delete(admin, "/api/podcasts/" + podcast).andExpect(status().isNoContent());
    }

    @Test
    void moderatorCanArchiveAndArchivedPodcastIsHiddenFromOthers() throws Exception {
        long podcast = podcastOf(alice);
        post(moderator, "/api/podcasts/" + podcast + "/archive")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podcast.status").value("ARCHIVED"));

        get(bob, "/api/podcasts/" + podcast).andExpect(status().isNotFound());
        get(bob, "/api/podcasts").andExpect(jsonPath("$[?(@.id == " + podcast + ")]").isEmpty());
        get(alice, "/api/podcasts/" + podcast).andExpect(status().isOk());
        get(moderator, "/api/podcasts/" + podcast).andExpect(status().isOk());

        post(bob, "/api/podcasts/" + podcast + "/restore").andExpect(status().isForbidden());
        post(alice, "/api/podcasts/" + podcast + "/restore")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        get(bob, "/api/podcasts/" + podcast).andExpect(status().isOk());
    }

    @Test
    void hiddenAndMissingObjectsAreIndistinguishableOnWrite() throws Exception {
        long podcast = podcastOf(alice);
        long episode = episodeOf(alice, podcast);

        delete(bob, "/api/episodes/" + episode).andExpect(status().isForbidden());
        delete(bob, "/api/episodes/999999999").andExpect(status().isForbidden());
        post(bob, "/api/episodes/" + episode + "/publish-now").andExpect(status().isForbidden());
        post(bob, "/api/episodes/999999999/publish-now").andExpect(status().isForbidden());
        delete(admin, "/api/episodes/999999999").andExpect(status().isNotFound());
    }

    @Test
    void unpublishedContentIsHiddenFromOtherUsers() throws Exception {
        long podcast = podcastOf(alice);
        long episode = episodeOf(alice, podcast);
        long file = audioOf(alice, episode, "audio/private.mp3", true);

        get(bob, "/api/episodes/" + episode).andExpect(status().isNotFound());
        get(bob, "/api/episodes").andExpect(jsonPath("$[?(@.id == " + episode + ")]").isEmpty());
        get(bob, "/api/audio-files/" + file).andExpect(status().isNotFound());
        get(alice, "/api/episodes/" + episode).andExpect(status().isOk());
        get(moderator, "/api/episodes/" + episode).andExpect(status().isOk());
        get(admin, "/api/audio-files/" + file).andExpect(status().isOk());

        post(alice, "/api/episodes/" + episode + "/publish-now").andExpect(status().isOk());
        get(bob, "/api/episodes/" + episode).andExpect(status().isOk());
        get(bob, "/api/audio-files/" + file).andExpect(status().isOk());
    }

    @Test
    void publicationSlotsAreVisibleOnlyToOwnerAndStaff() throws Exception {
        long podcast = podcastOf(alice);
        long episode = episodeOf(alice, podcast);
        audioOf(alice, episode, "audio/slot.mp3", true);
        long slot = createAs(alice, "/api/publication-slots", "{\"podcastId\":" + podcast + ",\"episodeId\":"
                + episode + ",\"scheduledAt\":\"" + Instant.now().plus(Duration.ofDays(1)) + "\"}");

        get(bob, "/api/publication-slots/" + slot).andExpect(status().isNotFound());
        get(bob, "/api/publication-slots").andExpect(jsonPath("$[?(@.id == " + slot + ")]").isEmpty());
        get(alice, "/api/publication-slots/" + slot).andExpect(status().isOk());
        get(moderator, "/api/publication-slots/" + slot).andExpect(status().isOk());

        patch(bob, "/api/publication-slots/" + slot, "{\"scheduledAt\":\"" + Instant.now().plus(Duration.ofDays(2)) + "\"}")
                .andExpect(status().isForbidden());
        post(bob, "/api/publication-slots/" + slot + "/cancel").andExpect(status().isForbidden());
        post(moderator, "/api/publication-slots/" + slot + "/publish").andExpect(status().isForbidden());
        post(moderator, "/api/publication-slots/" + slot + "/cancel")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void otherUsersCannotAddContentToSomeoneElsesPodcast() throws Exception {
        long podcast = podcastOf(alice);
        long episode = episodeOf(alice, podcast);
        long file = audioOf(alice, episode, "audio/own.mp3", false);

        post(bob, "/api/episodes", "{\"podcastId\":" + podcast + ",\"title\":\"Intruder\",\"durationSeconds\":60}")
                .andExpect(status().isForbidden());
        post(bob, "/api/audio-files", "{\"episodeId\":" + episode
                + ",\"path\":\"audio/intruder.mp3\",\"format\":\"MP3\",\"sizeBytes\":1}")
                .andExpect(status().isForbidden());
        post(bob, "/api/audio-files/" + file + "/make-main").andExpect(status().isForbidden());
        delete(bob, "/api/audio-files/" + file).andExpect(status().isForbidden());
        patch(bob, "/api/episodes/" + episode, "{\"title\":\"Hijacked\"}").andExpect(status().isForbidden());
        post(bob, "/api/episodes/" + episode + "/publish-now").andExpect(status().isForbidden());
        delete(bob, "/api/episodes/" + episode).andExpect(status().isForbidden());
        post(bob, "/api/publication-slots", "{\"podcastId\":" + podcast + ",\"episodeId\":" + episode
                + ",\"scheduledAt\":\"" + Instant.now().plus(Duration.ofDays(1)) + "\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticationAuthorizationAndBusinessErrorsAreDistinguished() throws Exception {
        long podcast = podcastOf(alice);
        long episode = episodeOf(alice, podcast);

        mvc.perform(MockMvcRequestBuilders.post("/api/episodes/" + episode + "/publish-now").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        post(bob, "/api/episodes/" + episode + "/publish-now")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        post(alice, "/api/episodes/" + episode + "/publish-now")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
        post(admin, "/api/episodes/999999999/publish-now")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void errorResponsesDoNotLeakInternalDetails() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/podcasts").with(TestSupport.as(alice.getUsername())).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request"))
                .andExpect(content().string(not(containsString("Exception"))));
        get(admin, "/api/no-such-endpoint")
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com.example"))));
    }
}
