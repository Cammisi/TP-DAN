package com.tpnube.fan_identity_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tpnube.fan_identity_service.dto.ErrorResponseDTO;
import com.tpnube.fan_identity_service.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtFilter,
                                                    ObjectMapper objectMapper) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // Igual que GlobalExceptionHandler: el 401 por falta/invalidez de JWT también
                    // tiene que respetar el formato único de error del contrato (T-01 sección 1).
                    // Esto pasa antes del DispatcherServlet, así que no lo cubre @RestControllerAdvice.
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json");
                    response.setCharacterEncoding("UTF-8");
                    ErrorResponseDTO body = new ErrorResponseDTO(
                            Instant.now(),
                            HttpStatus.UNAUTHORIZED.value(),
                            "UNAUTHORIZED",
                            "Se requiere autenticación",
                            request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getWriter(), body);
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/fans/auth/**", "/error").permitAll()
                // Los endpoints internos se protegen en T-41; por ahora se dejan accesibles
                // dentro de la red Docker (el gateway no los rutea, así que no llegan desde el host).
                .requestMatchers("/internal/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
