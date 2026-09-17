package com.tpnube.fan_identity_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret:default-dev-secret-key-que-tiene-al-menos-256-bits-para-hs256}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    public String getSecret() {
        return secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
