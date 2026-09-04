package com.misl.leavetracker.dto;

import com.misl.leavetracker.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Incoming payload for creating or updating an employee.
 *
 * Why a DTO instead of accepting the Employee entity directly:
 *   1. A client could otherwise POST {"id": 5, "role": "ADMIN"} and overwrite
 *      fields it has no business setting.
 *   2. The entity carries `leaveRequests`, which would drag the whole object
 *      graph into the JSON contract.
 *   3. The API shape can change without changing the database schema.
 *
 * Laravel comparison: this is the FormRequest - the annotations below are the
 * `rules()` array, and @Valid in the controller is what triggers them.
 */
public class EmployeeRequest {

    @NotBlank(message = "Employee code is required")
    @Size(max = 20, message = "Employee code must be at most 20 characters")
    private String employeeCode;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    /**
     * Deliberately NOT annotated @NotBlank.
     *
     * On CREATE the password is required; on UPDATE, leaving it blank means
     * "keep the existing password". A single annotation cannot express both, so
     * EmployeeService checks it explicitly. (The alternative - Bean Validation
     * groups - would work but adds machinery for one field.)
     */
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;

    @NotBlank(message = "Department is required")
    @Size(max = 100, message = "Department must be at most 100 characters")
    private String department;

    @NotBlank(message = "Designation is required")
    @Size(max = 100, message = "Designation must be at most 100 characters")
    private String designation;

    @NotNull(message = "Role is required")
    private Role role;

    /** Optional - defaults to true when omitted. */
    private Boolean active;

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
