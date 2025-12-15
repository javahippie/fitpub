package org.operaton.fitpub.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.RemoteActorRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the complete federation follow flow.
 * Tests the entire workflow from following a remote user to receiving accept notifications.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FederationFollowFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private RemoteActorRepository remoteActorRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    private User testUser;
    private String authToken;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        // Generate RSA key pair for ActivityPub
        KeyPair keyPair = generateRsaKeyPair();
        String publicKey = encodePublicKey(keyPair.getPublic().getEncoded());
        String privateKey = encodePrivateKey(keyPair.getPrivate().getEncoded());

        // Create test user
        testUser = User.builder()
            .username("testuser")
            .email("test@example.com")
            .passwordHash(passwordEncoder.encode("password123"))
            .displayName("Test User")
            .publicKey(publicKey)
            .privateKey(privateKey)
            .enabled(true)
            .build();
        testUser = userRepository.save(testUser);

        // Generate JWT token
        authToken = jwtTokenProvider.createToken(testUser.getUsername());
    }

    private KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private String encodePublicKey(byte[] keyBytes) {
        String base64 = Base64.getEncoder().encodeToString(keyBytes);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    private String encodePrivateKey(byte[] keyBytes) {
        String base64 = Base64.getEncoder().encodeToString(keyBytes);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    @Test
    @Disabled("Requires mocking external HTTP calls to WebFinger and remote ActivityPub servers")
    @DisplayName("Should follow a remote user via handle format @username@domain")
    void testFollowRemoteUserWithHandle() throws Exception {
        String remoteHandle = "@alice@fitpub.example";

        // Perform follow request
        MvcResult result = mockMvc.perform(post("/api/users/" + remoteHandle + "/follow")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();

        // Verify follow record was created with PENDING status
        String actorUri = baseUrl + "/users/alice"; // Would be resolved via WebFinger in real scenario
        Follow follow = followRepository.findByFollowerIdAndFollowingActorUri(testUser.getId(), actorUri)
            .orElse(null);

        // Note: In a real scenario, this would require mocking WebFinger discovery
        // For now, we verify the endpoint accepts the format
        assertThat(result.getResponse().getContentAsString()).contains("PENDING");
    }

    @Test
    @DisplayName("Should process incoming Follow activity and create follow relationship")
    void testProcessIncomingFollowActivity() throws Exception {
        // Create a remote actor
        RemoteActor remoteActor = RemoteActor.builder()
            .actorUri("https://remote.example/users/bob")
            .username("bob")
            .domain("remote.example")
            .displayName("Bob Remote")
            .inboxUrl("https://remote.example/users/bob/inbox")
            .outboxUrl("https://remote.example/users/bob/outbox")
            .publicKey("-----BEGIN PUBLIC KEY-----\ntest\n-----END PUBLIC KEY-----")
            .lastFetchedAt(Instant.now())
            .build();
        remoteActor = remoteActorRepository.save(remoteActor);

        // Create Follow activity
        String followId = "https://remote.example/activities/follow/" + UUID.randomUUID();
        Map<String, Object> followActivity = Map.of(
            "@context", "https://www.w3.org/ns/activitystreams",
            "type", "Follow",
            "id", followId,
            "actor", remoteActor.getActorUri(),
            "object", baseUrl + "/users/" + testUser.getUsername(),
            "published", Instant.now().toString()
        );

        // Post to inbox (without signature validation for test)
        mockMvc.perform(post("/users/" + testUser.getUsername() + "/inbox")
                .contentType("application/activity+json")
                .content(objectMapper.writeValueAsString(followActivity)))
            .andExpect(status().isAccepted());

        // Verify follow relationship was created
        Follow follow = followRepository.findByRemoteActorUriAndFollowingActorUri(
            remoteActor.getActorUri(),
            baseUrl + "/users/" + testUser.getUsername()
        ).orElse(null);

        assertThat(follow).isNotNull();
        assertThat(follow.getStatus()).isEqualTo(Follow.FollowStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Should process Accept activity and update follow status to ACCEPTED")
    void testProcessAcceptActivity() throws Exception {
        // Create a remote actor
        RemoteActor remoteActor = RemoteActor.builder()
            .actorUri("https://remote.example/users/carol")
            .username("carol")
            .domain("remote.example")
            .displayName("Carol Remote")
            .inboxUrl("https://remote.example/users/carol/inbox")
            .outboxUrl("https://remote.example/users/carol/outbox")
            .publicKey("-----BEGIN PUBLIC KEY-----\ntest\n-----END PUBLIC KEY-----")
            .lastFetchedAt(Instant.now())
            .build();
        remoteActor = remoteActorRepository.save(remoteActor);

        // Create pending follow
        String followActivityId = baseUrl + "/activities/follow/" + UUID.randomUUID();
        Follow pendingFollow = Follow.builder()
            .followerId(testUser.getId())
            .followingActorUri(remoteActor.getActorUri())
            .status(Follow.FollowStatus.PENDING)
            .activityId(followActivityId)
            .build();
        pendingFollow = followRepository.save(pendingFollow);

        // Create Accept activity
        Map<String, Object> acceptActivity = Map.of(
            "@context", "https://www.w3.org/ns/activitystreams",
            "type", "Accept",
            "id", "https://remote.example/activities/accept/" + UUID.randomUUID(),
            "actor", remoteActor.getActorUri(),
            "object", followActivityId
        );

        // Post Accept to inbox
        mockMvc.perform(post("/users/" + testUser.getUsername() + "/inbox")
                .contentType("application/activity+json")
                .content(objectMapper.writeValueAsString(acceptActivity)))
            .andExpect(status().isAccepted());

        // Verify follow status was updated to ACCEPTED
        Follow updatedFollow = followRepository.findById(pendingFollow.getId()).orElseThrow();
        assertThat(updatedFollow.getStatus()).isEqualTo(Follow.FollowStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Should process Undo Follow activity and remove follow relationship")
    void testProcessUndoFollowActivity() throws Exception {
        // Create a remote actor
        RemoteActor remoteActor = RemoteActor.builder()
            .actorUri("https://remote.example/users/dave")
            .username("dave")
            .domain("remote.example")
            .displayName("Dave Remote")
            .inboxUrl("https://remote.example/users/dave/inbox")
            .outboxUrl("https://remote.example/users/dave/outbox")
            .publicKey("-----BEGIN PUBLIC KEY-----\ntest\n-----END PUBLIC KEY-----")
            .lastFetchedAt(Instant.now())
            .build();
        remoteActor = remoteActorRepository.save(remoteActor);

        // Create accepted follow
        Follow acceptedFollow = Follow.builder()
            .remoteActorUri(remoteActor.getActorUri())
            .followingActorUri(baseUrl + "/users/" + testUser.getUsername())
            .status(Follow.FollowStatus.ACCEPTED)
            .build();
        acceptedFollow = followRepository.save(acceptedFollow);

        // Create Undo Follow activity
        Map<String, Object> undoActivity = Map.of(
            "@context", "https://www.w3.org/ns/activitystreams",
            "type", "Undo",
            "id", "https://remote.example/activities/undo/" + UUID.randomUUID(),
            "actor", remoteActor.getActorUri(),
            "object", Map.of(
                "type", "Follow",
                "actor", remoteActor.getActorUri(),
                "object", baseUrl + "/users/" + testUser.getUsername()
            )
        );

        // Post Undo to inbox
        mockMvc.perform(post("/users/" + testUser.getUsername() + "/inbox")
                .contentType("application/activity+json")
                .content(objectMapper.writeValueAsString(undoActivity)))
            .andExpect(status().isAccepted());

        // Verify follow was deleted
        boolean followExists = followRepository.existsById(acceptedFollow.getId());
        assertThat(followExists).isFalse();
    }

    @Test
    @DisplayName("Should return followers list including both local and remote followers")
    void testGetFollowersList() throws Exception {
        // Generate keypair for local follower
        KeyPair keyPair = generateRsaKeyPair();

        // Create a local follower
        User localFollower = User.builder()
            .username("localfollower")
            .email("local@example.com")
            .passwordHash(passwordEncoder.encode("password"))
            .displayName("Local Follower")
            .publicKey(encodePublicKey(keyPair.getPublic().getEncoded()))
            .privateKey(encodePrivateKey(keyPair.getPrivate().getEncoded()))
            .enabled(true)
            .build();
        localFollower = userRepository.save(localFollower);

        Follow localFollow = Follow.builder()
            .followerId(localFollower.getId())
            .followingActorUri(baseUrl + "/users/" + testUser.getUsername())
            .status(Follow.FollowStatus.ACCEPTED)
            .build();
        followRepository.save(localFollow);

        // Create a remote follower
        RemoteActor remoteFollower = RemoteActor.builder()
            .actorUri("https://remote.example/users/eve")
            .username("eve")
            .domain("remote.example")
            .displayName("Eve Remote")
            .inboxUrl("https://remote.example/users/eve/inbox")
            .outboxUrl("https://remote.example/users/eve/outbox")
            .publicKey("-----BEGIN PUBLIC KEY-----\ntest\n-----END PUBLIC KEY-----")
            .lastFetchedAt(Instant.now())
            .build();
        remoteFollower = remoteActorRepository.save(remoteFollower);

        Follow remoteFollow = Follow.builder()
            .remoteActorUri(remoteFollower.getActorUri())
            .followingActorUri(baseUrl + "/users/" + testUser.getUsername())
            .status(Follow.FollowStatus.ACCEPTED)
            .build();
        followRepository.save(remoteFollow);

        // Get followers list
        mockMvc.perform(get("/api/users/" + testUser.getUsername() + "/followers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.username == 'localfollower')]").exists())
            .andExpect(jsonPath("$[?(@.username == 'eve')]").exists())
            .andExpect(jsonPath("$[?(@.local == true)]").exists())
            .andExpect(jsonPath("$[?(@.local == false)]").exists());
    }

    @Test
    @DisplayName("Should return following list including both local and remote users")
    void testGetFollowingList() throws Exception {
        // Generate keypair for local followed user
        KeyPair keyPair = generateRsaKeyPair();

        // Create a local user being followed
        User localFollowed = User.builder()
            .username("localfollowed")
            .email("followed@example.com")
            .passwordHash(passwordEncoder.encode("password"))
            .displayName("Local Followed")
            .publicKey(encodePublicKey(keyPair.getPublic().getEncoded()))
            .privateKey(encodePrivateKey(keyPair.getPrivate().getEncoded()))
            .enabled(true)
            .build();
        localFollowed = userRepository.save(localFollowed);

        Follow localFollow = Follow.builder()
            .followerId(testUser.getId())
            .followingActorUri(baseUrl + "/users/" + localFollowed.getUsername())
            .status(Follow.FollowStatus.ACCEPTED)
            .build();
        followRepository.save(localFollow);

        // Create a remote user being followed
        RemoteActor remoteFollowed = RemoteActor.builder()
            .actorUri("https://remote.example/users/frank")
            .username("frank")
            .domain("remote.example")
            .displayName("Frank Remote")
            .inboxUrl("https://remote.example/users/frank/inbox")
            .outboxUrl("https://remote.example/users/frank/outbox")
            .publicKey("-----BEGIN PUBLIC KEY-----\ntest\n-----END PUBLIC KEY-----")
            .lastFetchedAt(Instant.now())
            .build();
        remoteFollowed = remoteActorRepository.save(remoteFollowed);

        Follow remoteFollow = Follow.builder()
            .followerId(testUser.getId())
            .followingActorUri(remoteFollowed.getActorUri())
            .status(Follow.FollowStatus.ACCEPTED)
            .build();
        followRepository.save(remoteFollow);

        // Get following list
        mockMvc.perform(get("/api/users/" + testUser.getUsername() + "/following"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.username == 'localfollowed')]").exists())
            .andExpect(jsonPath("$[?(@.username == 'frank')]").exists())
            .andExpect(jsonPath("$[?(@.local == true)]").exists())
            .andExpect(jsonPath("$[?(@.local == false)]").exists());
    }

    @Test
    @Disabled("Requires mocking external HTTP calls to WebFinger and remote ActivityPub servers")
    @DisplayName("Should prevent duplicate follow relationships")
    void testPreventDuplicateFollows() throws Exception {
        // Create a remote actor
        RemoteActor remoteActor = RemoteActor.builder()
            .actorUri("https://remote.example/users/grace")
            .username("grace")
            .domain("remote.example")
            .displayName("Grace Remote")
            .inboxUrl("https://remote.example/users/grace/inbox")
            .outboxUrl("https://remote.example/users/grace/outbox")
            .publicKey("-----BEGIN PUBLIC KEY-----\ntest\n-----END PUBLIC KEY-----")
            .lastFetchedAt(Instant.now())
            .build();
        remoteActor = remoteActorRepository.save(remoteActor);

        // Create existing follow
        Follow existingFollow = Follow.builder()
            .followerId(testUser.getId())
            .followingActorUri(remoteActor.getActorUri())
            .status(Follow.FollowStatus.ACCEPTED)
            .build();
        followRepository.save(existingFollow);

        // Try to follow again - should get appropriate response
        String remoteHandle = "@grace@remote.example";

        mockMvc.perform(post("/api/users/" + remoteHandle + "/follow")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().is4xxClientError()); // Should return error for duplicate follow
    }
}
