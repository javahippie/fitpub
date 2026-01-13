package net.javahippie.fitpub.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.javahippie.fitpub.model.entity.Activity;

/**
 * Request DTO for updating activity metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityUpdateRequest {

    @NotNull(message = "Title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Visibility is required")
    private Activity.Visibility visibility;

    private Boolean race; // Race/competition flag
}
