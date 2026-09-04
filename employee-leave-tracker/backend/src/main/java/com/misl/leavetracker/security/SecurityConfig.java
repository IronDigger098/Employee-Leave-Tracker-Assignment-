package com.misl.leavetracker.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * ============================================================================
 *  PHASE 2 VERSION - TEMPORARY.
 *  Every endpoint is open so that employee CRUD can be tested with curl before
 *  authentication exists. Phase 4 replaces the filter chain below with the real
 *  JWT-protected one. Nothing here is the final state except the PasswordEncoder.
 * ============================================================================
 *
 * Why this class must exist even now: spring-boot-starter-security is on the
 * classpath, and Spring Boot's default auto-configuration locks down EVERY
 * endpoint behind HTTP Basic with a random password printed at startup.
 * Defining our own SecurityFilterChain bean switches that default off.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * The password hashing strategy for the whole application, exposed as a bean
     * so EmployeeService (and later AuthService) can inject the interface rather
     * than construct a concrete encoder.
     *
     * BCrypt generates a random salt per password and embeds it in the 60-character
     * hash, which is why two employees with the same password get different hashes,
     * and why verification is encoder.matches(raw, hash) rather than
     * encode(raw).equals(hash) - encoding the same input twice never gives the
     * same output.
     *
     * Laravel comparison: Hash::make() / Hash::check(). Same BCrypt algorithm.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The security filter chain - the ordered list of servlet filters every
     * request passes through before reaching a controller.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                /*
                 * CSRF protection defends cookie-based sessions, where the browser
                 * attaches credentials automatically. This API is stateless and
                 * authenticated by an Authorization header that a third-party site
                 * cannot make the browser send, so CSRF tokens add nothing here.
                 */
                .csrf(csrf -> csrf.disable())

                // PHASE 2 ONLY: no authentication yet. Replaced in Phase 4.
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }
}
