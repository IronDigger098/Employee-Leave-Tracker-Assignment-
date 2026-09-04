package com.misl.leavetracker.exception;

/**
 * Thrown for business-rule violations that annotations cannot express, for example:
 *   - end date is before start date (spans two fields)
 *   - approving a leave request that is not PENDING (depends on current state)
 *   - creating an employee without a password
 *
 * Mapped to HTTP 400 by GlobalExceptionHandler.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
