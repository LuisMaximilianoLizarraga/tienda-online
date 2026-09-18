package com.lucho.tienda.security;

import com.lucho.tienda.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        // Inject a 256-bit (32 bytes) Base64 encoded key for testing
        String testSecretKey = "c3VwZXItc2VjcmV0LWtleS1mb3Itand0LXRlc3RpbmctYmFzZTY0LWVuY29kZWQ=";
        ReflectionTestUtils.setField(jwtUtils, "secretKey", testSecretKey);
        ReflectionTestUtils.setField(jwtUtils, "expirationTimeMs", 3600000L); // 1 hour
    }

    @Test
    @DisplayName("Should generate a valid token and correctly extract both username and userId")
    void generateAndValidateToken_Success() {
        // Arrange
        String expectedUsername = "admin";
        Long expectedUserId = 99L;

        // Act: Generate token with both claims
        String token = jwtUtils.generateToken(expectedUsername, expectedUserId);

        // Assert: Token is valid and claims match
        assertNotNull(token);
        assertTrue(jwtUtils.validateToken(token));

        assertEquals(expectedUsername, jwtUtils.getUsernameFromToken(token), "Username should match the subject claim");
        assertEquals(expectedUserId, jwtUtils.getUserIdFromToken(token), "UserId should match the custom claim");
    }

    @Test
    @DisplayName("Should return false when attempting to validate a malformed token")
    void validateToken_ReturnsFalse_WhenTokenIsInvalid() {
        // Arrange
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.payload";

        // Act & Assert
        assertFalse(jwtUtils.validateToken(invalidToken));
    }

    @Test
    @DisplayName("Should extract userId from valid Authorization header")
    void extractUserIdFromHeader_Success() {
        // Arrange
        Long expectedUserId = 99L;
        String token = jwtUtils.generateToken("admin", expectedUserId);
        String authHeader = "Bearer " + token;

        // Act
        Long extractedUserId = jwtUtils.extractUserIdFromHeader(authHeader);

        // Assert
        assertEquals(expectedUserId, extractedUserId, "Extracted ID should match the token claim");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when header is invalid or missing")
    void extractUserIdFromHeader_ThrowsException() {
        // Assert: Missing header
        assertThrows(UnauthorizedException.class, () ->
                jwtUtils.extractUserIdFromHeader(null), "Should throw exception when header is null");

        // Assert: Wrong prefix (e.g., Basic instead of Bearer)
        assertThrows(UnauthorizedException.class, () ->
                jwtUtils.extractUserIdFromHeader("Basic some-token-here"), "Should throw exception when missing Bearer prefix");

        // Assert: Empty token
        assertThrows(UnauthorizedException.class, () ->
                jwtUtils.extractUserIdFromHeader("Bearer "), "Should throw exception when token is empty");

        // Assert: Malformed or invalid token content (Triggers the catch block)
        assertThrows(UnauthorizedException.class, () ->
                        jwtUtils.extractUserIdFromHeader("Bearer invalid.or.malformed.jwt.token"),
                "Should throw exception when token parsing fails");
    }
}