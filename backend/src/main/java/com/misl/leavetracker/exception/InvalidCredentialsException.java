package com.misl.leavetracker.exception;

/**
 * Wrong email or wrong password. Mapped to HTTP 401 by GlobalExceptionHandler.
 *
 * The message is always the same generic sentence regardless of which half was
 * wrong. Saying "no account with that email" would let anyone test a list of
 * addresses against the system and learn which ones are real.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
