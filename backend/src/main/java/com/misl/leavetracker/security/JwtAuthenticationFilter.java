package com.misl.leavetracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before Spring Security's own authentication filters.
 *
 * Its whole job: if the request carries a valid "Authorization: Bearer <token>"
 * header, put the corresponding user into the SecurityContext. If it does not,
 * do nothing and let the request continue - unauthenticated. The decision about
 * whether that is acceptable belongs to the filter chain rules in SecurityConfig,
 * not to this filter. Keeping those two responsibilities separate is why a
 * missing token on a public endpoint (/api/auth/login) is fine, while the same
 * missing token on /api/employees produces a 401.
 *
 * Extending OncePerRequestFilter guarantees it runs a single time even when a
 * request is forwarded internally - a plain Filter can run twice.
 *
 * Laravel comparison: this is the 'auth' middleware, resolving the token into
 * the current user before the controller is reached.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(AUTH_HEADER);

        // No token at all: not an error here. Move on and let the chain decide.
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            // Verifies the signature and the expiry. Returns false instead of throwing.
            if (jwtService.isTokenValid(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                String email = jwtService.extractEmail(token);

                /*
                 * Deliberately reload the employee from the database rather than
                 * trusting the role claim inside the token. Costs one query per
                 * request, and buys correctness: an employee deactivated or
                 * demoted a minute ago loses access immediately instead of when
                 * their token expires 24 hours later.
                 */
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (userDetails.isEnabled()) {
                    /*
                     * The null second argument is the credentials slot. It is null
                     * on purpose: the password already did its job at login, and
                     * holding it in memory for every subsequent request would be a
                     * needless risk. This three-argument constructor is the
                     * "already authenticated" one - it marks the token authenticated.
                     */
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    // From here on, @PreAuthorize and @AuthenticationPrincipal work.
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            /*
             * Any failure means "not authenticated" - never "authenticated as
             * somebody". Clearing the context guarantees a half-built principal
             * cannot leak through, and the request proceeds to be rejected with
             * 401 by JwtAuthenticationEntryPoint.
             */
            SecurityContextHolder.clearContext();
            log.debug("Could not authenticate request with the supplied token: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
