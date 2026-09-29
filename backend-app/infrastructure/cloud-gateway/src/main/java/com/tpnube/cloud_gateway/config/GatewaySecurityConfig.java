package com.tpnube.cloud_gateway.config;

import com.tpnube.cloud_gateway.security.JwtValidationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Configuration
public class GatewaySecurityConfig {

    // Después del filtro de observación de Actuator (HIGHEST_PRECEDENCE + 1), así los 401/403 también se miden.
    private static final int CORS_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 2;
    private static final int JWT_FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 3;

    // CORS antes que JWT (ver explicacion_seguridad.md).
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(CORS_FILTER_ORDER);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<JwtValidationFilter> jwtValidationFilter(@Value("${jwt.secret}") String secret,
                                                                           JsonMapper jsonMapper) {
        FilterRegistrationBean<JwtValidationFilter> registration =
                new FilterRegistrationBean<>(new JwtValidationFilter(secret, jsonMapper));
        registration.setOrder(JWT_FILTER_ORDER);
        return registration;
    }
}
