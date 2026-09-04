package com.misl.leavetracker.service;

import com.misl.leavetracker.dto.LoginRequest;
import com.misl.leavetracker.dto.LoginResponse;
import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.exception.InvalidCredentialsException;
import com.misl.leavetracker.repository.EmployeeRepository;
import com.misl.leavetracker.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login. The only place a password is ever checked.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final EmployeeRepository employeeRepository;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager,
                       EmployeeRepository employeeRepository,
                       JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.employeeRepository = employeeRepository;
        this.jwtService = jwtService;
    }

    /**
     * Verify credentials, then issue a JWT.
     *
     * We hand the work to Spring Security's AuthenticationManager rather than
     * comparing hashes ourselves. It loads the user through
     * CustomUserDetailsService, runs passwordEncoder.matches() against the stored
     * BCrypt hash, and additionally enforces the account status flags - so a
     * deactivated employee (active = false, isEnabled() = false) is rejected here
     * with DisabledException without a single line of code on our side.
     *
     * Every failure mode - unknown email, wrong password, deactivated account -
     * is an AuthenticationException, and every one of them is converted into the
     * same 401 with the same message. Distinguishing them would leak which
     * accounts exist.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        // Authentication passed, so this employee is guaranteed to exist.
        Employee employee = employeeRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        String token = jwtService.generateToken(employee);

        return new LoginResponse(
                token,
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.getRole());
    }
}
