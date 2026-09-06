package com.misl.leavetracker.dto;

import com.misl.leavetracker.entity.Role;

/**
 * Outgoing representation of an employee.
 *
 * The single most important thing about this class is what it does NOT contain:
 * the password hash. Because every controller returns this type and never the
 * Employee entity, there is no code path that can leak a password hash - it is
 * structurally impossible rather than merely "we remembered to hide it".
 */
public class EmployeeResponse {

    private Long id;
    private String employeeCode;
    private String name;
    private String email;
    private String department;
    private String designation;
    private Role role;
    private boolean active;
    /** True once archived. The row is kept so leave history stays attached to a name. */
    private boolean deleted;

    public EmployeeResponse() {
    }

    public EmployeeResponse(Long id, String employeeCode, String name, String email,
                            String department, String designation, Role role, boolean active,
                            boolean deleted) {
        this.id = id;
        this.employeeCode = employeeCode;
        this.name = name;
        this.email = email;
        this.department = department;
        this.designation = designation;
        this.role = role;
        this.active = active;
        this.deleted = deleted;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public boolean isActive() {
        return active;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
