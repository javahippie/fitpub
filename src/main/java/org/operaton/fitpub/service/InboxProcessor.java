package org.operaton.fitpub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Processes incoming ActivityPub activities in the inbox.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InboxProcessor {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FederationService federationService;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Process an incoming activity.
     *
     * @param username the local username
     * @param activity the activity to process
     */
    @Transactional
    public void processActivity(String username, Map<String, Object> activity) {
        String type = (String) activity.get("type");
        log.info("Processing {} activity for user {}", type, username);

        switch (type) {
            case "Follow":
                processFollow(username, activity);
                break;
            case "Undo":
                processUndo(username, activity);
                break;
            case "Accept":
                processAccept(username, activity);
                break;
            case "Create":
                processCreate(username, activity);
                break;
            case "Like":
                processLike(username, activity);
                break;
            default:
                log.warn("Unhandled activity type: {}", type);
        }
    }

    /**
     * Process a Follow activity.
     * Remote user wants to follow local user.
     */
    private void processFollow(String username, Map<String, Object> activity) {
        try {
            String activityId = (String) activity.get("id");
            String actor = (String) activity.get("actor");
            String object = (String) activity.get("object");

            // Verify the follow is for the correct local user
            User localUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

            String expectedObjectUri = baseUrl + "/users/" + username;
            if (!object.equals(expectedObjectUri)) {
                log.warn("Follow object mismatch. Expected: {}, Got: {}", expectedObjectUri, object);
                return;
            }

            // Fetch remote actor information
            RemoteActor remoteActor = federationService.fetchRemoteActor(actor);

            // Check if follow already exists
            Follow existing = followRepository.findByActivityId(activityId).orElse(null);
            if (existing != null) {
                log.debug("Follow already processed: {}", activityId);
                return;
            }

            // Create follow relationship (as the object of the follow, from remote actor's perspective)
            // Here we store that the remote actor is following our local user
            // Note: We're storing it from the perspective of "who is following whom"
            Follow follow = Follow.builder()
                .followerId(null) // Remote actor, so no local user ID
                .followingActorUri(expectedObjectUri) // The local user being followed
                .status(Follow.FollowStatus.ACCEPTED) // Auto-accept for now
                .activityId(activityId)
                .build();

            followRepository.save(follow);

            // Send Accept activity
            federationService.sendAcceptActivity(follow, localUser);

            log.info("Processed Follow from {} for user {}", actor, username);

        } catch (Exception e) {
            log.error("Error processing Follow activity", e);
        }
    }

    /**
     * Process an Undo activity (e.g., unfollow).
     */
    private void processUndo(String username, Map<String, Object> activity) {
        try {
            Object object = activity.get("object");
            if (object instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> undoObject = (Map<String, Object>) object;
                String type = (String) undoObject.get("type");

                if ("Follow".equals(type)) {
                    String activityId = (String) undoObject.get("id");
                    Follow follow = followRepository.findByActivityId(activityId).orElse(null);
                    if (follow != null) {
                        followRepository.delete(follow);
                        log.info("Processed Undo Follow: {}", activityId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Undo activity", e);
        }
    }

    /**
     * Process an Accept activity (e.g., follow request accepted).
     */
    private void processAccept(String username, Map<String, Object> activity) {
        try {
            Object object = activity.get("object");
            if (object instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> acceptObject = (Map<String, Object>) object;
                String activityId = (String) acceptObject.get("id");

                Follow follow = followRepository.findByActivityId(activityId).orElse(null);
                if (follow != null && follow.getStatus() == Follow.FollowStatus.PENDING) {
                    follow.setStatus(Follow.FollowStatus.ACCEPTED);
                    followRepository.save(follow);
                    log.info("Follow request accepted: {}", activityId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Accept activity", e);
        }
    }

    /**
     * Process a Create activity (e.g., new post).
     */
    private void processCreate(String username, Map<String, Object> activity) {
        // TODO: Implement Create activity processing
        log.debug("Received Create activity for user {}", username);
    }

    /**
     * Process a Like activity.
     */
    private void processLike(String username, Map<String, Object> activity) {
        // TODO: Implement Like activity processing
        log.debug("Received Like activity for user {}", username);
    }
}
