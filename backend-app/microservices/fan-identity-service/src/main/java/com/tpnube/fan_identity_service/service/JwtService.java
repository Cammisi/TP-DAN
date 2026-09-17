package com.tpnube.fan_identity_service.service;

import com.tpnube.fan_identity_service.config.JwtConfig;
import com.tpnube.fan_identity_service.domain.Fan;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(JwtConfig jwtConfig) {
        this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = jwtConfig.getExpirationMs();
    }

    public String generateToken(Fan fan) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(fan.getId().toString())
                .claim("email", fan.getEmail())
                .claim("role", fan.getRole().name())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Valida el token y devuelve los claims. Lanza JwtException si es inválido o expirado.
     */
    public Claims validateTokenAndGetClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
