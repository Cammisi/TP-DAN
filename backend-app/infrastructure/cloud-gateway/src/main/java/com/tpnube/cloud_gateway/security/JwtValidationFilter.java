package com.tpnube.cloud_gateway.security;

import com.tpnube.cloud_gateway.dto.ErrorResponseDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class JwtValidationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtValidationFilter.class);

    private final SecretKey signingKey;
    private final JsonMapper jsonMapper;

    public JwtValidationFilter(String secret, JsonMapper jsonMapper) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isPublic(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Falta el token de autenticación");
            return;
        }

        String role;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authHeader.substring(7))
                    .getPayload();
            role = claims.get("role", String.class);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rechazado en {}: {}", request.getRequestURI(), e.getMessage());
            writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Token inválido o expirado");
            return;
        }

        if (isAdminOnly(request) && !"ADMIN".equals(role)) {
            writeError(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Se requiere rol administrador");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublic(HttpServletRequest request) {
        return path(request).startsWith("/api/fans/auth/");
    }

    // R-7 (ver explicacion_seguridad.md).
    private boolean isAdminOnly(HttpServletRequest request) {
        String path = path(request);
        if (path.startsWith("/api/matches") && !"GET".equals(request.getMethod())) {
            return true;
        }
        return path.startsWith("/api/alerts");
    }

    // Normalizado a propósito, no getRequestURI() (ver explicacion_seguridad.md).
    private String path(HttpServletRequest request) {
        return request.getServletPath();
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response,
                            HttpStatus status, String error, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        ErrorResponseDTO body = new ErrorResponseDTO(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI()
        );
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
