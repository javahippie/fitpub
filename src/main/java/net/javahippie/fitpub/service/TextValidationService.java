package net.javahippie.fitpub.service;

import lombok.RequiredArgsConstructor;
import net.javahippie.fitpub.config.FitPubTextLimitsProperties;
import net.javahippie.fitpub.exception.ApiValidationException;
import org.springframework.stereotype.Service;

/**
 * Central validation policy for configurable text fields.
 */
@Service
@RequiredArgsConstructor
public class TextValidationService {

    private final FitPubTextLimitsProperties textLimitsProperties;

    public int getUserBioMaxLength() {
        return textLimitsProperties.getUser().getBio().getMaxLength();
    }

    public int getActivityTitleMaxLength() {
        return textLimitsProperties.getActivity().getTitle().getMaxLength();
    }

    public int getActivityDescriptionMaxLength() {
        return textLimitsProperties.getActivity().getDescription().getMaxLength();
    }

    public void validateUserBio(String bio) {
        validateMaxLength(bio, getUserBioMaxLength(), "Bio");
    }

    public void validateActivityTitle(String title) {
        validateMaxLength(title, getActivityTitleMaxLength(), "Title");
    }

    public void validateActivityDescription(String description) {
        validateMaxLength(description, getActivityDescriptionMaxLength(), "Description");
    }

    private void validateMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new ApiValidationException(fieldName + " must not exceed " + maxLength + " characters");
        }
    }
}
