package com.misl.leavetracker.dto;

import com.misl.leavetracker.entity.Role;

/**
 * What a successful login returns.
 *
 * The token is the only field the server cares about on subsequent requests.
 * The id, name, email and role are included so the Angular app can render the
 * navbar and route to the right dashboard immediately, without decoding the JWT
 * itself or making a second "who am I" call.
 *
 * The role here drives NAVIGATION only. Every protected endpoint re-checks the
 * role server-side, so a user who edits the value in localStorage just reaches a
 * page whose API calls all return 403.
 */
public class LoginResponse {

    private String token;
    private Long employeeId;
    private String name;
    private String email;
    private Role role;

    public LoginResponse(String token, Long employeeId, String name, String email, Role role) {
        this.token = token;
        this.employeeId = employeeId;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Role getRole() {
        return role;
    }
}
