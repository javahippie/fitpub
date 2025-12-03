package org.operaton.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.ActorDTO;
import org.operaton.fitpub.model.dto.UserDTO;
import org.operaton.fitpub.model.dto.UserUpdateRequest;
import org.operaton.fitpub.model.entity.Follow;
import org.operaton.fitpub.model.entity.RemoteActor;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.RemoteActorRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
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
}
