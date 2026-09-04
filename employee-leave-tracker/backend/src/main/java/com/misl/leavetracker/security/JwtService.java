package com.misl.leavetracker.security;

import com.misl.leavetracker.entity.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates and verifies JSON Web Tokens.
 *
 * A JWT is three base64url segments joined by dots:
 *
 *     header.payload.signature
 *
 *   header    - which algorithm signed this ({"alg":"HS256"})
 *   payload   - the claims: who the user is, when it expires
 *   signature - HMAC-SHA256 over (header + "." + payload) using our secret key
 *
 * The payload is only ENCODED, not encrypted - anyone can paste a token into
 * jwt.io and read it. That is why no password or other secret ever goes in a
 * claim. What the signature guarantees is that nobody EDITED the payload:
 * change a single character and the signature no longer matches, because
 * producing a valid signature requires the secret key, which only the server has.
 *
 * That is what makes the API stateless. The server stores no sessions; it can
 * trust a token purely because the signature verifies.
 *
 * Laravel comparison: closest to a signed, self-contained Sanctum token - except
 * Sanctum stores tokens in a database table and can revoke them, while a JWT is
 * valid until it expires. That trade-off (no server state vs no revocation) is a
 * likely interview question.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * @Value injects a property from application.yml. Both values come from
     * environment variables in Docker (JWT_SECRET, JWT_EXPIRATION_MS) and fall
     * back to development defaults locally.
     */
    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        // HS256 requires a key of at least 256 bits (32 bytes). Keys.hmacShaKeyFor
        // throws immediately at startup if the configured secret is shorter -
        // failing loudly on boot rather than silently on the first login.
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Issues a token for a successfully authenticated employee.
     *
     * The email goes in the standard "subject" claim. The id and role are extra
     * claims, carried purely for convenience on the client side - the Angular app
     * reads the role to decide which dashboard to show. The SERVER never trusts
     * the role claim for authorization: JwtAuthenticationFilter reloads the
     * employee from the database on every request, so a role changed in the
     * database takes effect immediately rather than at token expiry.
     */
    public String generateToken(Employee employee) {
        Date issuedAt = new Date();
        Date expiresAt = new Date(issuedAt.getTime() + expirationMs);

        return Jwts.builder()
                .subject(employee.getEmail())
                .claim("employeeId", employee.getId())
                .claim("role", employee.getRole().name())
                .issuedAt(issuedAt)
                .expiration(expiresAt)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Reads the email out of a token. Throws JwtException if the token is invalid. */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Verifies signature and expiry without throwing.
     *
     * parseSignedClaims does the real work: it recomputes the signature with our
     * key and compares, then checks the exp claim against the clock. A tampered
     * payload, a token signed with a different secret, or an expired token all
     * land in the catch block.
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
