package com.lucho.tienda.security;

import com.lucho.tienda.constant.ErrorMessageConstants;
import com.lucho.tienda.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtils {

    private static final String CLAIM_USER_ID = "userId";

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expirationTimeMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String username, Long userId) {
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTimeMs))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.get(CLAIM_USER_ID, Long.class);
    }
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long extractUserIdFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // 1. Extract and sanitize the token by removing any accidental whitespaces
            String token = authHeader.substring(7).trim();

            // 2. Ensure the token is not an empty string after trimming
            if (!token.isEmpty()) {
                try {
                    // 3. Attempt to parse the token (this may throw exceptions if invalid)
                    return getUserIdFromToken(token);
                } catch (Exception e) {
                    // 4. Catch ANY parsing error (expired, malformed, empty) and throw a 401 Unauthorized
                    throw new UnauthorizedException(ErrorMessageConstants.INVALID_OR_MISSING_TOKEN);
                }
            }
        }

        // Throw 401 if the header is null, lacks the Bearer prefix, or is completely empty
        throw new UnauthorizedException(ErrorMessageConstants.INVALID_OR_MISSING_TOKEN);
    }
}