package net.javahippie.fitpub.model.dto;

import lombok.Value;

/**
 * Standard error payload for API responses.
 */
@Value
public class ApiError {

    String code;
    String message;
}
