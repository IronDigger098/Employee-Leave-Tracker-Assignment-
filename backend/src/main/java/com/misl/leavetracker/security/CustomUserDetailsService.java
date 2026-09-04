package com.misl.leavetracker.security;

import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.repository.EmployeeRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teaches Spring Security how to find a user in OUR database.
 *
 * Spring Security ships an in-memory user store by default - that is the source of
 * the "Using generated security password: ..." line we saw in earlier phases.
 * Declaring this bean replaces it: Boot detects a UserDetailsService plus a
 * PasswordEncoder in the context and wires a DaoAuthenticationProvider around them
 * automatically. The generated-password warning disappears from the startup log,
 * which is a nice way to confirm this class was picked up.
 *
 * Called from exactly two places:
 *   1. AuthenticationManager during login, to fetch the stored hash for comparison.
 *   2. JwtAuthenticationFilter on every authenticated request, to rebuild the
 *      principal from the email inside the token.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    public CustomUserDetailsService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * The interface calls the parameter "username"; in this application it is the email.
     *
     * Throwing UsernameNotFoundException rather than returning null is required by
     * the contract. Spring Security deliberately converts it into the same generic
     * BadCredentialsException it uses for a wrong password, so an attacker cannot
     * tell "no such account" apart from "wrong password" - that difference would
     * let them enumerate valid emails.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No employee found with email " + email));
        return new EmployeeUserDetails(employee);
    }
}
