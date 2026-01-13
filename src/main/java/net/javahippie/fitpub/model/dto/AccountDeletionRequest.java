package net.javahippie.fitpub.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for account deletion requiring password confirmation.
 * Used to securely verify the user's identity before permanently deleting their account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeletionRequest {

    /**
     * The user's current password for verification.
     * Required to prevent accidental or unauthorized account deletion.
     */
    @NotBlank(message = "Password is required to confirm account deletion")
    private String password;
}
