package net.javahippie.fitpub.model.dto;

import jakarta.validation.constraints.NotNull;
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
    private String title;

    private String description;

    @NotNull(message = "Visibility is required")
    private Activity.Visibility visibility;

    private Activity.ActivityType activityType;

    private Boolean race; // Race/competition flag
}
