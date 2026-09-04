package com.misl.leavetracker.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials posted to /api/auth/login.
 *
 * Validation here is only a courtesy - an empty email is rejected with 400 before
 * the database is touched. A wrong-but-well-formed email still comes back as 401,
 * never 404, so the endpoint reveals nothing about which accounts exist.
 */
public class LoginRequest {

    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
