package net.javahippie.fitpub.model.dto;
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

    private String title;

    private String description;

    private Activity.Visibility visibility;
}
