package org.operaton.fitpub.repository;

import org.operaton.fitpub.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity.
 * Provides methods for user authentication and discovery.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Finds a user by username.
     * Used for login and WebFinger discovery.
     *
     * @param username the username
     * @return optional user
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user by email.
     * Used for login and duplicate email checking.
     *
     * @param email the email address
     * @return optional user
     */
    Optional<User> findByEmail(String email);

    /**
     * Checks if a username already exists.
     * Used during registration.
     *
     * @param username the username to check
     * @return true if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Checks if an email already exists.
     * Used during registration.
     *
     * @param email the email to check
     * @return true if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Finds all enabled users.
     * Used for administrative purposes.
     *
     * @return list of enabled users
     */
    Optional<User> findByUsernameAndEnabledTrue(String username);
}
