package com.misl.leavetracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.misl.leavetracker.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Produces the 401 response for requests that reach a protected endpoint without
 * valid authentication.
 *
 * Why this class is needed at all: GlobalExceptionHandler cannot help here.
 * @RestControllerAdvice only sees exceptions thrown from inside a controller, and
 * an unauthenticated request is rejected by the security FILTER CHAIN - before
 * any controller is chosen. Without this bean Spring Security would send its
 * default empty 401, and the Angular error handler would receive no JSON body.
 *
 * Writing the same {@link ApiError} shape used everywhere else means the frontend
 * has exactly one error format to parse, whether the failure came from a filter
 * or from a service.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    /**
     * Spring Boot already configures an ObjectMapper (with Java-time support for
     * the LocalDateTime inside ApiError), so we inject that one rather than
     * constructing a second, differently-configured mapper.
     */
    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiError error = new ApiError(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                "Authentication required. Send a valid 'Authorization: Bearer <token>' header.",
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
