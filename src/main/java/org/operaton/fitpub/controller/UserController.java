package org.operaton.fitpub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.UserDTO;
import org.operaton.fitpub.model.dto.UserUpdateRequest;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
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

        return ResponseEntity.ok(UserDTO.fromEntity(user));
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

        return ResponseEntity.ok(UserDTO.fromEntity(updated));
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

        return ResponseEntity.ok(UserDTO.fromEntity(user));
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

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }
}
