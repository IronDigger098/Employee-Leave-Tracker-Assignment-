package com.misl.leavetracker.security;

import com.misl.leavetracker.entity.Employee;
import com.misl.leavetracker.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService}.
 *
 * No mocks and no Spring context: JwtService takes its two settings as plain
 * constructor arguments, so a test can build one directly. That is a side benefit
 * of constructor injection worth noticing — a class whose dependencies arrive
 * through its constructor is trivially testable, while one that reads them from a
 * static or a field-injected bean is not.
 */
@DisplayName("JwtService")
class JwtServiceTest {

    /** HS256 needs at least 256 bits (32 bytes). This is a test-only value. */
    private static final String SECRET = "test-secret-key-for-unit-tests-0123456789abcdef";
    private static final long ONE_HOUR = 3_600_000L;
    /** Long, not long - Employee.setId takes a Long. */
    private static final Long EMPLOYEE_ID = 2L;

    private JwtService jwtService;
    private Employee employee;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, ONE_HOUR);

        employee = new Employee("EMP001", "Rahim Uddin", "rahim@misl.com",
                "hashed", "Engineering", "Software Engineer", Role.EMPLOYEE, true);
        employee.setId(EMPLOYEE_ID);
    }

    @Test
    @DisplayName("produces a three-part token")
    void tokenHasThreeParts() {
        String token = jwtService.generateToken(employee);

        // header.payload.signature
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("round-trips the email through the subject claim")
    void extractsTheEmailItStored() {
        String token = jwtService.generateToken(employee);

        assertThat(jwtService.extractEmail(token)).isEqualTo("rahim@misl.com");
    }

    @Test
    @DisplayName("accepts a token it issued itself")
    void acceptsItsOwnToken() {
        assertThat(jwtService.isTokenValid(jwtService.generateToken(employee))).isTrue();
    }

    @Test
    @DisplayName("rejects a token whose payload has been tampered with")
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(employee);

        // Flip one character in the payload segment. The signature was computed
        // over the original bytes, so verification must now fail - this is the
        // property the whole stateless design rests on.
        String[] parts = token.split("\\.");
        char first = parts[1].charAt(0);
        parts[1] = (first == 'A' ? 'B' : 'A') + parts[1].substring(1);
        String tampered = String.join(".", parts);

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsTokenFromAnotherIssuer() {
        JwtService otherServer = new JwtService(
                "a-completely-different-secret-key-9876543210zyxwvu", ONE_HOUR);
        String foreignToken = otherServer.generateToken(employee);

        assertThat(jwtService.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpiredToken() {
        // A negative lifetime issues a token whose exp is already in the past.
        JwtService alreadyExpired = new JwtService(SECRET, -1000L);

        assertThat(jwtService.isTokenValid(alreadyExpired.generateToken(employee))).isFalse();
    }

    @Test
    @DisplayName("rejects a string that is not a token at all")
    void rejectsGarbage() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }
}
