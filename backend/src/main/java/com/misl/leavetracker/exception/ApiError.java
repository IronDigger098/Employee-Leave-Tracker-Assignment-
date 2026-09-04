package com.misl.leavetracker.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The single JSON shape every error response uses:
 *
 * {
 *   "timestamp": "2026-09-03T18:20:11.482",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Validation failed",
 *   "path": "/api/employees",
 *   "fieldErrors": { "email": "Email must be a valid email address" }
 * }
 *
 * A consistent error contract means the Angular side needs exactly one piece of
 * error-handling code, not one per endpoint.
 *
 * @JsonInclude(NON_NULL) omits fieldErrors entirely when there are none, so a
 * 404 response is not cluttered with "fieldErrors": null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    /** field name -> validation message. Only populated for validation failures. */
    private Map<String, String> fieldErrors;

    public ApiError(int status, String error, String message, String path) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.path = path;
    }

    public ApiError(int status, String error, String message, String path,
                    Map<String, String> fieldErrors) {
        this(status, error, message, path);
        this.fieldErrors = fieldErrors;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
