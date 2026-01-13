package net.javahippie.fitpub.model.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.javahippie.fitpub.model.entity.Activity;

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

    private Activity.Visibility visibility;
}
