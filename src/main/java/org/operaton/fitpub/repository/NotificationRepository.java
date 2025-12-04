package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Notification entity.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * Find all notifications for a user, ordered by creation date (newest first).
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return page of notifications
     */
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId ORDER BY n.createdAt DESC")
    Page<Notification> findByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Find unread notifications for a user, ordered by creation date (newest first).
     *
     * @param userId the user ID
     * @param pageable pagination parameters
     * @return page of unread notifications
     */
    @Query("SELECT n FROM Notification n WHERE n.user.id = :userId AND n.read = false ORDER BY n.createdAt DESC")
    Page<Notification> findUnreadByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Count unread notifications for a user.
     *
     * @param userId the user ID
     * @return count of unread notifications
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.read = false")
    long countUnreadByUserId(@Param("userId") UUID userId);

    /**
     * Mark all notifications as read for a user.
     *
     * @param userId the user ID
     * @return number of notifications marked as read
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.read = false")
    int markAllAsReadByUserId(@Param("userId") UUID userId);

    /**
     * Delete old read notifications for a user (older than a specified date).
     *
     * @param userId the user ID
     * @param cutoffDate the cutoff date
     * @return number of notifications deleted
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.id = :userId AND n.read = true AND n.createdAt < :cutoffDate")
    int deleteOldReadNotifications(@Param("userId") UUID userId, @Param("cutoffDate") java.time.LocalDateTime cutoffDate);
}
