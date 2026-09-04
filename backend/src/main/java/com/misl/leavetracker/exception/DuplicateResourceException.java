package com.misl.leavetracker.exception;

/**
 * Thrown when a value that must be unique already exists (email, employee code).
 * Mapped to HTTP 409 Conflict by GlobalExceptionHandler.
 *
 * We check with an exists* query BEFORE saving rather than catching the database
 * constraint violation afterwards. Both work, but the pre-check lets us return a
 * clear message naming the offending field instead of a raw Postgres error.
 * The database UNIQUE constraint stays as the real guarantee.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
