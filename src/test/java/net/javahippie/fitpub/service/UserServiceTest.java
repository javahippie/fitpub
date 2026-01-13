package net.javahippie.fitpub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import net.javahippie.fitpub.model.entity.User;
import net.javahippie.fitpub.repository.FollowRepository;
import net.javahippie.fitpub.repository.UserRepository;
import net.javahippie.fitpub.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService, focusing on account deletion functionality.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private FederationService federationService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UUID testUserId;
    private String testPassword = "password123";
    private String encodedPassword = "$2a$10$encodedPasswordHash";

    @BeforeEach
    void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .username("testuser")
                .email("test@example.com")
                .passwordHash(encodedPassword)
                .displayName("Test User")
                .enabled(true)
                .locked(false)
                .build();

        // Set the base URL for the service
        ReflectionTestUtils.setField(userService, "baseUrl", "https://fitpub.example");
    }

    @Test
    @DisplayName("Should successfully delete account with valid password")
    void deleteUserAccount_WithValidPassword_ShouldSucceed() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(followRepository.deleteByFollowingActorUri(anyString())).thenReturn(2);
        doNothing().when(federationService).sendActorDeleteActivity(any(User.class));
        doNothing().when(userRepository).delete(any(User.class));

        // Act & Assert
        assertDoesNotThrow(() -> userService.deleteUserAccount(testUserId, testPassword));

        // Verify interactions
        verify(userRepository).findById(testUserId);
        verify(passwordEncoder).matches(testPassword, encodedPassword);
        verify(federationService).sendActorDeleteActivity(testUser);
        verify(followRepository).deleteByFollowingActorUri("https://fitpub.example/users/testuser");
        verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("Should throw BadCredentialsException with invalid password")
    void deleteUserAccount_WithInvalidPassword_ShouldThrowBadCredentialsException() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUserAccount(testUserId, testPassword))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid password");

        // Verify that deletion was not attempted
        verify(userRepository).findById(testUserId);
        verify(passwordEncoder).matches(testPassword, encodedPassword);
        verify(federationService, never()).sendActorDeleteActivity(any());
        verify(followRepository, never()).deleteByFollowingActorUri(anyString());
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when user not found")
    void deleteUserAccount_WithNonExistentUser_ShouldThrowIllegalArgumentException() {
        // Arrange
        UUID nonExistentUserId = UUID.randomUUID();
        when(userRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUserAccount(nonExistentUserId, testPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User not found");

        // Verify that no further processing occurred
        verify(userRepository).findById(nonExistentUserId);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(federationService, never()).sendActorDeleteActivity(any());
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should continue deletion even if federation fails")
    void deleteUserAccount_WhenFederationFails_ShouldContinueWithDeletion() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(followRepository.deleteByFollowingActorUri(anyString())).thenReturn(1);

        // Simulate federation failure
        doThrow(new RuntimeException("Federation service unavailable"))
                .when(federationService).sendActorDeleteActivity(any(User.class));

        doNothing().when(userRepository).delete(any(User.class));

        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> userService.deleteUserAccount(testUserId, testPassword));

        // Verify deletion still occurred
        verify(federationService).sendActorDeleteActivity(testUser);
        verify(followRepository).deleteByFollowingActorUri("https://fitpub.example/users/testuser");
        verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("Should delete follow relationships where user is being followed")
    void deleteUserAccount_ShouldDeleteFollowRelationships() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(testPassword, encodedPassword)).thenReturn(true);
        when(followRepository.deleteByFollowingActorUri("https://fitpub.example/users/testuser")).thenReturn(5);
        doNothing().when(federationService).sendActorDeleteActivity(any(User.class));
        doNothing().when(userRepository).delete(any(User.class));

        // Act
        userService.deleteUserAccount(testUserId, testPassword);

        // Assert
        verify(followRepository).deleteByFollowingActorUri("https://fitpub.example/users/testuser");
    }

    @Test
    @DisplayName("Should handle null password gracefully")
    void deleteUserAccount_WithNullPassword_ShouldThrowBadCredentialsException() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(null, encodedPassword)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUserAccount(testUserId, null))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid password");

        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should handle empty password gracefully")
    void deleteUserAccount_WithEmptyPassword_ShouldThrowBadCredentialsException() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("", encodedPassword)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUserAccount(testUserId, ""))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid password");

        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should verify password before attempting any deletions")
    void deleteUserAccount_ShouldVerifyPasswordBeforeDeletion() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", encodedPassword)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUserAccount(testUserId, "wrongpassword"))
                .isInstanceOf(BadCredentialsException.class);

        // Verify order: password check happens before any deletion attempts
        verify(passwordEncoder).matches("wrongpassword", encodedPassword);
        verify(federationService, never()).sendActorDeleteActivity(any());
        verify(followRepository, never()).deleteByFollowingActorUri(anyString());
        verify(userRepository, never()).delete(any());
    }
}
