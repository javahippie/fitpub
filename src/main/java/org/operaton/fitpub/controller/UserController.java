package org.operaton.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.UserDTO;
import org.operaton.fitpub.model.dto.UserUpdateRequest;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.FollowRepository;
import org.operaton.fitpub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

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
}
