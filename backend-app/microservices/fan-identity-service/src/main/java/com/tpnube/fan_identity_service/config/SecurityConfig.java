package com.tpnube.fan_identity_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpnube.fan_identity_service.dto.ErrorResponseDTO;
import com.tpnube.fan_identity_service.security.InternalApiKeyFilter;
import com.tpnube.fan_identity_service.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtFilter,
                                                    InternalApiKeyFilter internalApiKeyFilter,
                                                    ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // El 401 y el 403 pasan antes del DispatcherServlet (no los cubre GlobalExceptionHandler):
            // se escriben a mano para respetar el formato único de error del contrato (T-01 sección 1).
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    writeError(objectMapper, request, response, HttpStatus.UNAUTHORIZED,
                            "UNAUTHORIZED", "Se requiere autenticación"))
                .accessDeniedHandler((request, response, accessDeniedException) ->
                    writeError(objectMapper, request, response, HttpStatus.FORBIDDEN,
                            "FORBIDDEN", "No tenés permiso para acceder a este recurso"))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/fans/auth/**", "/error", "/actuator/**").permitAll()
                .requestMatchers("/internal/**").hasRole("INTERNAL")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(internalApiKeyFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
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
        objectMapper.writeValue(response.getWriter(), body);
    }
}
