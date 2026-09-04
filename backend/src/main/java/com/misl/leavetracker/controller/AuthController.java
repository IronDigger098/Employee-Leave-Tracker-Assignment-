package com.misl.leavetracker.controller;

import com.misl.leavetracker.dto.LoginRequest;
import com.misl.leavetracker.dto.LoginResponse;
import com.misl.leavetracker.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only endpoint reachable without a token.
 *
 * SecurityConfig permits /api/auth/** explicitly; everything else in the
 * application requires authentication by default.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login
     *   200 + { token, employeeId, name, email, role }  on success
     *   401                                             on bad credentials
     *   400                                             on an empty email/password
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
