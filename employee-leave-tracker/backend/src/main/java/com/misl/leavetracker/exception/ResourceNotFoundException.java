package com.misl.leavetracker.exception;

/**
 * Thrown when an entity referenced by id does not exist.
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 *
 * It extends RuntimeException (unchecked) on purpose: service methods should not
 * have to declare `throws` for a condition that every caller handles identically.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    /** Convenience: new ResourceNotFoundException("Employee", 7) -> "Employee not found with id 7" */
    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id " + id);
    }
}
