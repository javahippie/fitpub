package net.javahippie.fitpub.service;

import net.javahippie.fitpub.config.FitPubTextLimitsProperties;
import net.javahippie.fitpub.exception.ApiValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("TextValidationService Tests")
class TextValidationServiceTest {

    @Test
    @DisplayName("Should allow user bio up to configured max length")
    void shouldAllowUserBioUpToConfiguredMaxLength() {
        TextValidationService service = new TextValidationService(properties(12, 20, 30));

        assertDoesNotThrow(() -> service.validateUserBio("123456789012"));
    }

    @Test
    @DisplayName("Should reject user bio longer than configured max length")
    void shouldRejectUserBioLongerThanConfiguredMaxLength() {
        TextValidationService service = new TextValidationService(properties(12, 20, 30));

        assertThrows(ApiValidationException.class, () -> service.validateUserBio("1234567890123"));
    }

    @Test
    @DisplayName("Should allow activity title up to configured max length")
    void shouldAllowActivityTitleUpToConfiguredMaxLength() {
        TextValidationService service = new TextValidationService(properties(12, 10, 30));

        assertDoesNotThrow(() -> service.validateActivityTitle("1234567890"));
    }

    @Test
    @DisplayName("Should reject activity title longer than configured max length")
    void shouldRejectActivityTitleLongerThanConfiguredMaxLength() {
        TextValidationService service = new TextValidationService(properties(12, 10, 30));

        assertThrows(ApiValidationException.class, () -> service.validateActivityTitle("12345678901"));
    }

    @Test
    @DisplayName("Should allow activity description up to configured max length")
    void shouldAllowActivityDescriptionUpToConfiguredMaxLength() {
        TextValidationService service = new TextValidationService(properties(12, 10, 20));

        assertDoesNotThrow(() -> service.validateActivityDescription("12345678901234567890"));
    }

    @Test
    @DisplayName("Should reject activity description longer than configured max length")
    void shouldRejectActivityDescriptionLongerThanConfiguredMaxLength() {
        TextValidationService service = new TextValidationService(properties(12, 10, 20));

        assertThrows(ApiValidationException.class, () -> service.validateActivityDescription("123456789012345678901"));
    }

    private FitPubTextLimitsProperties properties(int bioMaxLength, int titleMaxLength, int descriptionMaxLength) {
        FitPubTextLimitsProperties properties = new FitPubTextLimitsProperties();
        properties.getUser().getBio().setMaxLength(bioMaxLength);
        properties.getActivity().getTitle().setMaxLength(titleMaxLength);
        properties.getActivity().getDescription().setMaxLength(descriptionMaxLength);
        return properties;
    }
}
