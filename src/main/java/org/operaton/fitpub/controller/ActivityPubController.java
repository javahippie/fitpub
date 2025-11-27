package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.activitypub.Actor;
import org.operaton.fitpub.model.activitypub.OrderedCollection;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * ActivityPub protocol controller.
 * Implements ActivityPub server-to-server (S2S) protocol endpoints.
 *
 * Spec: https://www.w3.org/TR/activitypub/
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ActivityPubController {

    private final UserRepository userRepository;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    private static final String ACTIVITY_JSON = "application/activity+json";
    private static final String LD_JSON = "application/ld+json; profile=\"https://www.w3.org/ns/activitystreams\"";

    /**
     * Actor profile endpoint.
     * Returns the ActivityPub Actor object for a user.
     *
     * @param username the username
     * @return Actor object in JSON-LD format
     */
    @GetMapping(
        value = "/users/{username}",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<Actor> getActor(@PathVariable String username) {
        log.debug("ActivityPub actor request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("User not found for ActivityPub request: {}", username);
            return ResponseEntity.notFound().build();
        }

        User user = userOpt.get();
        Actor actor = Actor.fromUser(user, baseUrl);

        return ResponseEntity.ok(actor);
    }

    /**
     * Inbox endpoint for receiving ActivityPub activities.
     * POST /users/{username}/inbox
     *
     * @param username the username
     * @param activity the incoming activity
     * @return accepted response
     */
    @PostMapping(
        value = "/users/{username}/inbox",
        consumes = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<Void> inbox(
        @PathVariable String username,
        @RequestBody Map<String, Object> activity,
        @RequestHeader(value = "Signature", required = false) String signature
    ) {
        log.info("Received ActivityPub activity for user {}: {}", username, activity.get("type"));

        // TODO: Validate HTTP signature
        // TODO: Process activity based on type (Follow, Like, Create, etc.)

        // For MVP, just accept all activities
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Outbox endpoint for user's activities.
     * GET /users/{username}/outbox
     *
     * @param username the username
     * @return OrderedCollection of activities
     */
    @GetMapping(
        value = "/users/{username}/outbox",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<OrderedCollection> outbox(@PathVariable String username) {
        log.debug("Outbox request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String outboxUrl = baseUrl + "/users/" + username + "/outbox";

        // TODO: Fetch actual activities from database
        OrderedCollection collection = OrderedCollection.empty(outboxUrl);

        return ResponseEntity.ok(collection);
    }

    /**
     * Followers collection endpoint.
     * GET /users/{username}/followers
     *
     * @param username the username
     * @return OrderedCollection of followers
     */
    @GetMapping(
        value = "/users/{username}/followers",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<OrderedCollection> followers(@PathVariable String username) {
        log.debug("Followers request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String followersUrl = baseUrl + "/users/" + username + "/followers";

        // TODO: Fetch actual followers from database
        OrderedCollection collection = OrderedCollection.empty(followersUrl);

        return ResponseEntity.ok(collection);
    }

    /**
     * Following collection endpoint.
     * GET /users/{username}/following
     *
     * @param username the username
     * @return OrderedCollection of following
     */
    @GetMapping(
        value = "/users/{username}/following",
        produces = {ACTIVITY_JSON, LD_JSON, MediaType.APPLICATION_JSON_VALUE}
    )
    public ResponseEntity<OrderedCollection> following(@PathVariable String username) {
        log.debug("Following request for user: {}", username);

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String followingUrl = baseUrl + "/users/" + username + "/following";

        // TODO: Fetch actual following from database
        OrderedCollection collection = OrderedCollection.empty(followingUrl);

        return ResponseEntity.ok(collection);
    }
}
