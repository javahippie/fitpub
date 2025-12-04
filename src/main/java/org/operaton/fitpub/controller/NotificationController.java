package org.operaton.fitpub.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.operaton.fitpub.model.dto.NotificationDTO;
import org.operaton.fitpub.model.entity.Notification;
import org.operaton.fitpub.model.entity.User;
import org.operaton.fitpub.repository.UserRepository;
import org.operaton.fitpub.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for notification operations.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * Helper method to get user from authenticated UserDetails.
     */
    private User getUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    /**
     * Get all notifications for the authenticated user.
     *
     * @param userDetails the authenticated user
     * @param pageable pagination parameters
     * @return page of notifications
     */
    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getNotifications(
        @AuthenticationPrincipal UserDetails userDetails,
        Pageable pageable
    ) {
        User user = getUser(userDetails);
        Page<Notification> notifications = notificationService.getNotifications(user.getId(), pageable);
        Page<NotificationDTO> notificationDTOs = notifications.map(NotificationDTO::fromEntity);
        return ResponseEntity.ok(notificationDTOs);
    }

    /**
     * Get unread notifications for the authenticated user.
     *
     * @param userDetails the authenticated user
     * @param pageable pagination parameters
     * @return page of unread notifications
     */
    @GetMapping("/unread")
    public ResponseEntity<Page<NotificationDTO>> getUnreadNotifications(
        @AuthenticationPrincipal UserDetails userDetails,
        Pageable pageable
    ) {
        User user = getUser(userDetails);
        Page<Notification> notifications = notificationService.getUnreadNotifications(user.getId(), pageable);
        Page<NotificationDTO> notificationDTOs = notifications.map(NotificationDTO::fromEntity);
        return ResponseEntity.ok(notificationDTOs);
    }

    /**
     * Get count of unread notifications.
     *
     * @param userDetails the authenticated user
     * @return count of unread notifications
     */
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);
        long count = notificationService.countUnreadNotifications(user.getId());
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Mark a notification as read.
     *
     * @param notificationId the notification ID
     * @param userDetails the authenticated user
     * @return no content
     */
    @PutMapping("/{notificationId}/read")
    @Transactional
    public ResponseEntity<Void> markAsRead(
        @PathVariable UUID notificationId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);
        boolean success = notificationService.markAsRead(notificationId, user.getId());

        if (!success) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    /**
     * Mark all notifications as read.
     *
     * @param userDetails the authenticated user
     * @return number of notifications marked as read
     */
    @PutMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Integer>> markAllAsRead(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);
        int count = notificationService.markAllAsRead(user.getId());
        Map<String, Integer> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a notification.
     *
     * @param notificationId the notification ID
     * @param userDetails the authenticated user
     * @return no content
     */
    @DeleteMapping("/{notificationId}")
    @Transactional
    public ResponseEntity<Void> deleteNotification(
        @PathVariable UUID notificationId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = getUser(userDetails);
        boolean success = notificationService.deleteNotification(notificationId, user.getId());

        if (!success) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
