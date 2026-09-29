package com.tpnube.alert_dispatcher_service.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

// Sin @Configuration a propósito: así solo la usa FanIdentityClient (ver gotcha de Feign en CLAUDE.md).
public class FanIdentityClientConfiguration {

    @Bean
    public RequestInterceptor internalApiKeyInterceptor(@Value("${internal.api-key}") String apiKey) {
        return template -> template.header("X-Internal-Api-Key", apiKey);
    }
}
