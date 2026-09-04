package com.misl.leavetracker.entity;

/**
 * Who a user is allowed to be in this system.
 *
 * Persisted as a STRING (see @Enumerated in Employee), so the database column
 * holds 'ADMIN' or 'EMPLOYEE' rather than 0 or 1.
 *
 * Spring Security prefixes role names with "ROLE_" by convention, so this
 * becomes the authority "ROLE_ADMIN" / "ROLE_EMPLOYEE" at runtime. That is why
 * @PreAuthorize("hasRole('ADMIN')") is written without the prefix but
 * hasAuthority() would need "ROLE_ADMIN".
 */
public enum Role {
    ADMIN,
    EMPLOYEE
}
