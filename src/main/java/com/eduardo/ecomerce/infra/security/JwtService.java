package com.eduardo.ecomerce.infra.security;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    public String generateToken(UserDetails userDetails) {
        return buildToken(userDetails, expiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        com.eduardo.ecomerce.domain.user.User user =
                (com.eduardo.ecomerce.domain.user.User) userDetails;

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("typ", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    private String buildToken(UserDetails userDetails, long expirationTime) {
        com.eduardo.ecomerce.domain.user.User user =
                (com.eduardo.ecomerce.domain.user.User) userDetails;

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", user.getRole().name())
                .claim("name", user.getName())
                .claim("id", user.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claimsResolver.apply(claims);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("typ", String.class));
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }
}
