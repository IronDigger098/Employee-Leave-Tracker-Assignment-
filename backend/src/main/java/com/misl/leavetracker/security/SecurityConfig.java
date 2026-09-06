package com.misl.leavetracker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The security configuration for the application.
 *
 * (Phase 2 had a placeholder version of this class that permitted everything so
 * CRUD could be tested before login existed. This is the real one.)
 *
 * @EnableMethodSecurity switches on @PreAuthorize. Without it those annotations
 * are silently ignored - the code compiles, the app starts, and every role check
 * quietly does nothing. Worth remembering: a missing @EnableMethodSecurity is one
 * of the classic "why is my security not working" bugs.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    }

    /**
     * BCrypt hashing for the whole application.
     *
     * BCrypt generates a random salt per password and stores it inside the
     * 60-character hash, so two employees with the same password get different
     * hashes. That is why verification must be encoder.matches(raw, hash) and
     * never encode(raw).equals(storedHash) - encoding the same input twice never
     * produces the same output.
     *
     * Laravel comparison: Hash::make() / Hash::check(). Same algorithm.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the AuthenticationManager so AuthService can inject it.
     *
     * Note what is NOT declared here: a DaoAuthenticationProvider. Spring Boot
     * builds one automatically because the context contains a UserDetailsService
     * (CustomUserDetailsService) and a PasswordEncoder (above). Declaring it by
     * hand would be duplicated wiring.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * CSRF protects cookie-based sessions, where the browser attaches
                 * credentials to cross-site requests automatically. This API is
                 * authenticated by an Authorization header that a third-party page
                 * cannot make the browser send, so a CSRF token would guard nothing.
                 */
                .csrf(csrf -> csrf.disable())

                /*
                 * STATELESS: never create an HttpSession, never look for one.
                 * Each request must prove itself with its own token. This is the
                 * line that makes the API horizontally scalable - any instance can
                 * serve any request, because no instance holds session state.
                 */
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Login must be reachable without a token - chicken and egg.
                        .requestMatchers("/api/auth/**").permitAll()

                        /*
                         * Swagger UI and the OpenAPI document it renders.
                         *
                         * These must be public for the same practical reason as
                         * login: the page has to load before you can authenticate
                         * through it. Without this rule every Swagger URL returns
                         * the JSON 401 from JwtAuthenticationEntryPoint and the UI
                         * never appears.
                         *
                         * Exposing the API description is fine here - it documents
                         * the shape of the endpoints, not any data, and every one of
                         * them still enforces its own authentication and role rules.
                         * A production deployment would normally disable springdoc
                         * entirely outside development
                         * (springdoc.api-docs.enabled=false).
                         */
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**").permitAll()
                        /*
                         * Everything else needs authentication. Note the default is
                         * DENY: a new endpoint added later is protected automatically
                         * rather than being accidentally public. Fine-grained ROLE
                         * rules live on the controllers as @PreAuthorize, so the
                         * rule sits next to the method it guards.
                         */
                        .anyRequest().authenticated())

                // Returns our JSON ApiError on 401 instead of an empty response body.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))

                /*
                 * Order matters. Our filter must run BEFORE
                 * UsernamePasswordAuthenticationFilter so that by the time Spring
                 * Security checks "is this request authenticated?", the token has
                 * already been turned into a populated SecurityContext.
                 */
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
