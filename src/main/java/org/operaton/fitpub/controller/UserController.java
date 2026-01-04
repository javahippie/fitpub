package org.operaton.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.AccountDeletionRequest;
import org.operaton.fitpub.model.dto.ActorDTO;
import org.operaton.fitpub.model.dto.UserDTO;
import org.operaton.fitpub.model.dto.UserUpdateRequest;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.RemoteActorRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller for user profile operations.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final RemoteActorRepository remoteActorRepository;
    private final org.operaton.fitpub.service.WebFingerClient webFingerClient;
    private final org.operaton.fitpub.service.FederationService federationService;
    private final UserService userService;

    @Value("${fitpub.base-url}")
    private String baseUrl;

    /**
     * Helper method to populate follower/following counts in UserDTO.
     */
    private void populateSocialCounts(UserDTO dto, User user) {
        String actorUri = user.getActorUri(baseUrl);

        // Count followers (people following this user)
        long followersCount = followRepository.countAcceptedFollowersByActorUri(actorUri);

        // Count following (people this user follows)
        long followingCount = followRepository.findAcceptedFollowingByUserId(user.getId()).size();

        dto.setFollowersCount(followersCount);
        dto.setFollowingCount((long) followingCount);
    }

    /**
     * Get current user's profile.
     *
     * @param userDetails the authenticated user
     * @return user profile
     */
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        log.debug("User {} retrieving own profile", userDetails.getUsername());

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserDTO dto = UserDTO.fromEntity(user);
        populateSocialCounts(dto, user);

        return ResponseEntity.ok(dto);
    }

    /**
     * Update current user's profile.
     *
     * @param request the update request
     * @param userDetails the authenticated user
     * @return updated user profile
     */
    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateCurrentUser(
        @Valid @RequestBody UserUpdateRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} updating profile", userDetails.getUsername());

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Update allowed fields
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl().trim());
        }

        User updated = userRepository.save(user);

        UserDTO dto = UserDTO.fromEntity(updated);
        populateSocialCounts(dto, updated);

        return ResponseEntity.ok(dto);
    }

    /**
     * Delete current user's account.
     * Requires password confirmation for security.
     * Sends ActivityPub Delete activity to notify followers.
     * Permanently deletes all user data.
     *
     * @param request the deletion request with password
     * @param userDetails the authenticated user
     * @return success or error response
     */
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> deleteCurrentUser(
        @Valid @RequestBody AccountDeletionRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} requesting account deletion", userDetails.getUsername());

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        try {
            userService.deleteUserAccount(user.getId(), request.getPassword());

            log.info("Account deletion successful: {}", user.getUsername());
            return ResponseEntity.ok(Map.of(
                "message", "Account deleted successfully",
                "username", user.getUsername()
            ));

        } catch (BadCredentialsException e) {
            log.warn("Invalid password for account deletion: {}", user.getUsername());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid password"));

        } catch (Exception e) {
            log.error("Account deletion failed for {}", user.getUsername(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to delete account: " + e.getMessage()
            ));
        }
    }

    /**
     * Get user profile by username.
     *
     * @param username the username
     * @return user profile
     */
    @GetMapping("/{username}")
    public ResponseEntity<UserDTO> getUserByUsername(@PathVariable String username) {
        log.debug("Retrieving profile for username: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        UserDTO dto = UserDTO.fromEntity(user);
        populateSocialCounts(dto, user);

        return ResponseEntity.ok(dto);
    }

    /**
     * Get user profile by ID.
     *
     * @param id the user ID
     * @return user profile
     */
    @GetMapping("/id/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID id) {
        log.debug("Retrieving profile for user ID: {}", id);

        User user = userRepository.findById(id)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UserDTO dto = UserDTO.fromEntity(user);
        populateSocialCounts(dto, user);

        return ResponseEntity.ok(dto);
    }

    /**
     * Search for users by username or display name.
     *
     * @param query search query
     * @param pageable pagination parameters (page, size, sort)
     * @return page of matching users
     */
    @GetMapping("/search")
    public ResponseEntity<Page<UserDTO>> searchUsers(
        @RequestParam("q") String query,
        Pageable pageable
    ) {
        log.debug("Searching users with query: {}, page: {}, size: {}",
            query, pageable.getPageNumber(), pageable.getPageSize());

        Page<User> users = userRepository.searchUsers(query, pageable);
        Page<UserDTO> userDTOs = users.map(user -> {
            UserDTO dto = UserDTO.fromEntity(user);
            populateSocialCounts(dto, user);
            return dto;
        });

        return ResponseEntity.ok(userDTOs);
    }

    /**
     * Browse all enabled users.
     *
     * @param pageable pagination parameters (page, size, sort)
     * @return page of users
     */
    @GetMapping("/browse")
    public ResponseEntity<Page<UserDTO>> browseUsers(Pageable pageable) {
        log.debug("Browsing all users, page: {}, size: {}",
            pageable.getPageNumber(), pageable.getPageSize());

        Page<User> users = userRepository.findAllEnabledUsers(pageable);
        Page<UserDTO> userDTOs = users.map(user -> {
            UserDTO dto = UserDTO.fromEntity(user);
            populateSocialCounts(dto, user);
            return dto;
        });

        return ResponseEntity.ok(userDTOs);
    }

    /**
     * Discover a remote user via WebFinger.
     * Takes a handle in the format @username@domain or username@domain,
     * performs WebFinger discovery, fetches the remote actor, and returns actor information.
     *
     * @param handle the handle of the remote user (@username@domain)
     * @param userDetails the authenticated user making the request
     * @return ActorDTO containing remote user information
     */
    @GetMapping("/discover-remote")
    public ResponseEntity<ActorDTO> discoverRemoteUser(
        @RequestParam String handle,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} discovering remote user: {}", userDetails.getUsername(), handle);

        try {
            // 1. WebFinger lookup to discover actor URI
            String actorUri = webFingerClient.discoverActor(handle);
            log.debug("Discovered actor URI: {}", actorUri);

            // 2. Fetch remote actor information
            RemoteActor remoteActor = federationService.fetchRemoteActor(actorUri);
            log.debug("Fetched remote actor: {}", remoteActor.getUsername());

            // 3. Convert to DTO and return (no follow relationship yet, so followedAt is null)
            ActorDTO dto = ActorDTO.fromRemoteActor(remoteActor, null);
            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            log.error("Invalid handle format: {}", handle, e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error discovering remote user: {}", handle, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get list of followers for a user.
     *
     * @param username the username
     * @return list of followers
     */
    @GetMapping("/{username}/followers")
    public ResponseEntity<List<ActorDTO>> getFollowers(@PathVariable String username) {
        log.debug("Retrieving followers for user: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        String actorUri = user.getActorUri(baseUrl);
        List<Follow> followers = followRepository.findAcceptedFollowersByActorUri(actorUri);

        List<ActorDTO> actorDTOs = new ArrayList<>();
        for (Follow follow : followers) {
            // For each follower, check if it's a local user or remote actor
            if (follow.getFollowerId() != null) {
                // Local follower
                userRepository.findById(follow.getFollowerId()).ifPresent(follower -> {
                    actorDTOs.add(ActorDTO.fromLocalUser(follower, baseUrl, follow.getCreatedAt()));
                });
            } else if (follow.getRemoteActorUri() != null) {
                // Remote follower
                remoteActorRepository.findByActorUri(follow.getRemoteActorUri()).ifPresent(remoteActor -> {
                    actorDTOs.add(ActorDTO.fromRemoteActor(remoteActor, follow.getCreatedAt()));
                });
            }
        }

        log.debug("Found {} followers for user {}", actorDTOs.size(), username);
        return ResponseEntity.ok(actorDTOs);
    }

    /**
     * Get list of users that this user is following.
     *
     * @param username the username
     * @return list of following
     */
    @GetMapping("/{username}/following")
    public ResponseEntity<List<ActorDTO>> getFollowing(@PathVariable String username) {
        log.debug("Retrieving following for user: {}", username);

        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<Follow> following = followRepository.findAcceptedFollowingByUserId(user.getId());

        List<ActorDTO> actorDTOs = new ArrayList<>();
        for (Follow follow : following) {
            String followingActorUri = follow.getFollowingActorUri();

            // Check if it's a local user by trying to extract username from actor URI
            // Format: https://fitpub.example/users/username
            if (followingActorUri.startsWith(baseUrl)) {
                String followingUsername = followingActorUri.substring(
                    followingActorUri.lastIndexOf("/") + 1
                );
                userRepository.findByUsername(followingUsername).ifPresent(followedUser -> {
                    actorDTOs.add(ActorDTO.fromLocalUser(followedUser, baseUrl, follow.getCreatedAt()));
                });
            } else {
                // Remote actor
                remoteActorRepository.findByActorUri(followingActorUri).ifPresent(remoteActor -> {
                    actorDTOs.add(ActorDTO.fromRemoteActor(remoteActor, follow.getCreatedAt()));
                });
            }
        }

        log.debug("Found {} following for user {}", actorDTOs.size(), username);
        return ResponseEntity.ok(actorDTOs);
    }

    /**
     * Follow a user (local or remote).
     *
     * @param username the username to follow (local username or @username@domain format)
     * @param userDetails the authenticated user
     * @return success response
     */
    @PostMapping("/{username}/follow")
    public ResponseEntity<Map<String, Object>> followUser(
        @PathVariable String username,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} attempting to follow {}", userDetails.getUsername(), username);

        // Get the current user
        User currentUser = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("Current user not found"));

        // Check if this is a remote user (contains @ and position > 0)
        boolean isRemoteUser = username.contains("@") && username.indexOf("@") > 0;

        if (isRemoteUser) {
            // Remote user follow
            return followRemoteUser(username, currentUser);
        } else {
            // Local user follow
            return followLocalUser(username, currentUser);
        }
    }

    /**
     * Follow a local user (auto-accepted).
     */
    private ResponseEntity<Map<String, Object>> followLocalUser(String username, User currentUser) {
        // Get the user to follow
        User userToFollow = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Cannot follow yourself
        if (currentUser.getId().equals(userToFollow.getId())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Cannot follow yourself"));
        }

        String followingActorUri = userToFollow.getActorUri(baseUrl);

        // Check if already following
        Optional<Follow> existingFollow = followRepository.findByFollowerIdAndFollowingActorUri(
            currentUser.getId(), followingActorUri
        );

        if (existingFollow.isPresent()) {
            if (existingFollow.get().getStatus() == Follow.FollowStatus.ACCEPTED) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Already following this user"));
            } else {
                // Update existing pending follow to accepted
                Follow follow = existingFollow.get();
                follow.setStatus(Follow.FollowStatus.ACCEPTED);
                followRepository.save(follow);
                log.info("Updated pending follow to accepted: {} -> {}", currentUser.getUsername(), username);
            }
        } else {
            // Create new follow relationship
            String activityId = baseUrl + "/activities/follow/" + UUID.randomUUID();
            Follow follow = Follow.builder()
                .followerId(currentUser.getId())
                .followingActorUri(followingActorUri)
                .status(Follow.FollowStatus.ACCEPTED) // Auto-accept for local-to-local follows
                .activityId(activityId)
                .build();
            followRepository.save(follow);
            log.info("Created new follow: {} -> {}", currentUser.getUsername(), username);
        }

        // Get updated follower count
        long followersCount = followRepository.countAcceptedFollowersByActorUri(followingActorUri);

        return ResponseEntity.ok(Map.of(
            "message", "Successfully followed " + username,
            "followersCount", followersCount
        ));
    }

    /**
     * Follow a remote user via ActivityPub (requires Accept from remote).
     */
    private ResponseEntity<Map<String, Object>> followRemoteUser(String handle, User currentUser) {
        try {
            log.info("Following remote user: {}", handle);

            // 1. Discover remote actor using WebFinger
            String remoteActorUri = webFingerClient.discoverActor(handle);
            log.debug("Discovered remote actor URI: {}", remoteActorUri);

            // 2. Check if already following
            Optional<Follow> existingFollow = followRepository.findByFollowerIdAndFollowingActorUri(
                currentUser.getId(), remoteActorUri
            );

            if (existingFollow.isPresent()) {
                Follow follow = existingFollow.get();
                if (follow.getStatus() == Follow.FollowStatus.ACCEPTED) {
                    return ResponseEntity.badRequest()
                        .body(Map.of("error", "Already following this user"));
                } else {
                    return ResponseEntity.ok(Map.of(
                        "message", "Follow request already pending for " + handle,
                        "status", "PENDING"
                    ));
                }
            }

            // 3. Send Follow activity to remote actor
            // This will also create a PENDING follow record
            federationService.sendFollowActivity(remoteActorUri, currentUser);

            return ResponseEntity.ok(Map.of(
                "message", "Follow request sent to " + handle,
                "status", "PENDING",
                "note", "Waiting for acceptance from remote user"
            ));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid handle format: {}", handle, e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid handle format: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to follow remote user: {}", handle, e);
            return ResponseEntity.status(500)
                .body(Map.of("error", "Failed to follow remote user: " + e.getMessage()));
        }
    }

    /**
     * Unfollow a user.
     *
     * @param username the username to unfollow
     * @param userDetails the authenticated user
     * @return success response
     */
    @DeleteMapping("/{username}/follow")
    public ResponseEntity<Map<String, Object>> unfollowUser(
        @PathVariable String username,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        log.info("User {} attempting to unfollow {}", userDetails.getUsername(), username);

        // Get the current user
        User currentUser = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("Current user not found"));

        // Get the user to unfollow
        User userToUnfollow = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        String followingActorUri = userToUnfollow.getActorUri(baseUrl);

        // Find the follow relationship
        Optional<Follow> follow = followRepository.findByFollowerIdAndFollowingActorUri(
            currentUser.getId(), followingActorUri
        );

        if (follow.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Not following this user"));
        }

        // Delete the follow relationship
        followRepository.delete(follow.get());
        log.info("Deleted follow: {} -> {}", currentUser.getUsername(), username);

        // Get updated follower count
        long followersCount = followRepository.countAcceptedFollowersByActorUri(followingActorUri);

        return ResponseEntity.ok(Map.of(
            "message", "Successfully unfollowed " + username,
            "followersCount", followersCount
        ));
    }

    /**
     * Check if the current user is following a specific user.
     *
     * @param username the username to check
     * @param userDetails the authenticated user
     * @return follow status
     */
    @GetMapping("/{username}/follow-status")
    public ResponseEntity<Map<String, Boolean>> getFollowStatus(
        @PathVariable String username,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.ok(Map.of("isFollowing", false));
        }

        User currentUser = userRepository.findByUsername(userDetails.getUsername())
            .orElse(null);

        if (currentUser == null) {
            return ResponseEntity.ok(Map.of("isFollowing", false));
        }

        User targetUser = userRepository.findByUsername(username)
            .orElse(null);

        if (targetUser == null) {
            return ResponseEntity.ok(Map.of("isFollowing", false));
        }

        String followingActorUri = targetUser.getActorUri(baseUrl);
        Optional<Follow> follow = followRepository.findByFollowerIdAndFollowingActorUri(
            currentUser.getId(), followingActorUri
        );

        boolean isFollowing = follow.isPresent() && follow.get().getStatus() == Follow.FollowStatus.ACCEPTED;

        return ResponseEntity.ok(Map.of("isFollowing", isFollowing));
    }
}
