package com.misl.leavetracker.security;

import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The bridge between our {@link Employee} entity and Spring Security's
 * {@link UserDetails} contract.
 *
 * Many tutorials make the entity itself implement UserDetails. This project keeps
 * them apart on purpose:
 *   - Employee is a persistence concern (JPA annotations, database columns).
 *   - UserDetails is a security concern (authorities, account status flags).
 * Merging them would put getAuthorities() and isAccountNonLocked() on a @Entity
 * class, and Hibernate would need @Transient on each. Two small classes are
 * cleaner than one confused one.
 *
 * This object is what {@code @AuthenticationPrincipal} injects into a controller,
 * so it also carries the employee id - which is how endpoints know WHO is asking
 * without a second database lookup.
 */
public class EmployeeUserDetails implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String password;
    private final Role role;
    private final boolean active;

    public EmployeeUserDetails(Employee employee) {
        this.id = employee.getId();
        this.name = employee.getName();
        this.email = employee.getEmail();
        this.password = employee.getPassword();
        this.role = employee.getRole();
        this.active = employee.isActive();
    }

    /**
     * Spring Security has no concept of "roles" separate from authorities - a role
     * is just an authority whose name starts with "ROLE_". That prefix is why
     * hasRole('ADMIN') and hasAuthority('ROLE_ADMIN') mean the same thing, and why
     * we must add the prefix here.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Spring calls the login identifier "username"; ours happens to be the email. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Returning false here makes Spring Security reject the login with
     * DisabledException before our code runs - so deactivating an employee
     * locks them out with no extra check anywhere.
     */
    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    public Long getId() {
        return id;
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

    /** Convenience used by services to decide between "see everything" and "see own". */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
