package org.operaton.fitpub.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.operaton.fitpub.model.entity.Activity;

/**
 * Request DTO for uploading a new activity.
 * Used with multipart file upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityUploadRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @NotNull(message = "Visibility is required")
    private Activity.Visibility visibility;
}
