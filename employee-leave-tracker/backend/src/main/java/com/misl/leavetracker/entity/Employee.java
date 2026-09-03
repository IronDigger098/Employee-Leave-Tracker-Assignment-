package com.misl.leavetracker.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * An employee of the company. This single entity also serves as the
 * authentication user - the email is the login identifier and the password
 * column holds a BCrypt hash.
 *
 * Laravel comparison: this is the Eloquent model AND the User model merged,
 * except a JPA entity holds no query methods at all. Queries live in
 * EmployeeRepository.
 *
 * Why one table instead of separate `users` and `employees`: every user in this
 * system IS an employee (the admin is an HR employee), so splitting them would
 * add a join and a synchronisation problem for zero benefit at this scope.
 */
@Entity
@Table(
        name = "employees",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_employees_email", columnNames = "email"),
                @UniqueConstraint(name = "uk_employees_code", columnNames = "employee_code")
        }
)
public class Employee {

    /**
     * GenerationType.IDENTITY delegates id generation to the database column
     * (Postgres BIGSERIAL). Hibernate reads the generated value back after insert.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, length = 20)
    private String employeeCode;

    @Column(nullable = false, length = 100)
    private String name;

    /** Login identifier. Unique constraint above is what makes the 409 case detectable. */
    @Column(nullable = false, length = 150)
    private String email;

    /**
     * BCrypt hash - never a plaintext password, and never exposed through any DTO.
     * A BCrypt hash is always 60 characters; 100 leaves room for the algorithm to change.
     */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 100)
    private String department;

    @Column(nullable = false, length = 100)
    private String designation;

    /**
     * EnumType.STRING stores 'ADMIN' / 'EMPLOYEE' in the column.
     *
     * The JPA default is EnumType.ORDINAL, which stores the enum's index (0, 1, ...).
     * That is a trap: the day someone inserts a new constant in the middle of the enum,
     * every existing row silently changes meaning. STRING is the safe default.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Soft on/off switch. An inactive employee is rejected at login. */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Inverse side of the one-to-many relationship.
     *
     * - mappedBy = "employee" tells Hibernate the FK is owned by LeaveRequest.employee,
     *   so this side creates no extra column or join table.
     * - cascade = ALL + orphanRemoval = true means deleting an employee deletes their
     *   leave requests, instead of the database rejecting the delete with a foreign
     *   key violation.
     * - It is LAZY by default (OneToMany always is), so loading an employee does not
     *   drag in their whole leave history.
     */
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LeaveRequest> leaveRequests = new ArrayList<>();

    /** JPA requires a no-argument constructor to instantiate entities via reflection. */
    public Employee() {
    }

    public Employee(String employeeCode, String name, String email, String password,
                    String department, String designation, Role role, boolean active) {
        this.employeeCode = employeeCode;
        this.name = name;
        this.email = email;
        this.password = password;
        this.department = department;
        this.designation = designation;
        this.role = role;
        this.active = active;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<LeaveRequest> getLeaveRequests() {
        return leaveRequests;
    }

    public void setLeaveRequests(List<LeaveRequest> leaveRequests) {
        this.leaveRequests = leaveRequests;
    }
}
