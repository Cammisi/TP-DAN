package com.tpnube.alert_dispatcher_service;

import com.tpnube.alert_dispatcher_service.service.FanIdentityLookupService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.FeignClientProperties;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AlertDispatcherServiceApplicationTests {

	private final FeignClientProperties feignClientProperties;
	private final FeignClientFactory feignClientFactory;
	private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

	@Autowired
	AlertDispatcherServiceApplicationTests(FeignClientProperties feignClientProperties,
										   FeignClientFactory feignClientFactory,
										   CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
		this.feignClientProperties = feignClientProperties;
		this.feignClientFactory = feignClientFactory;
		this.circuitBreakerFactory = circuitBreakerFactory;
	}

	@Test
	void contextLoads() {
	}

	// R-10 (ver gotcha de Feign en CLAUDE.md).
	@Test
	void fanIdentityClientTimeoutIsBound() {
		FeignClientProperties.FeignClientConfiguration config =
				feignClientProperties.getConfig().get("fan-identity-service");
		assertEquals(1000, config.getConnectTimeout());
		assertEquals(3000, config.getReadTimeout());
	}

	@Test
	void fanIdentityClientSendsInternalApiKey() {
		RequestTemplate template = new RequestTemplate();
		feignClientFactory.getInstances("fan-identity-service", RequestInterceptor.class)
				.values()
				.forEach(interceptor -> interceptor.apply(template));

		assertEquals(List.of("dev-internal-api-key"), List.copyOf(template.headers().get("X-Internal-Api-Key")));
	}

	@Test
	void internalApiKeyIsNotSentByOtherFeignClients() {
		Map<String, RequestInterceptor> interceptors =
				feignClientFactory.getInstances("otro-servicio", RequestInterceptor.class);

		assertTrue(interceptors == null || interceptors.isEmpty());
	}

	// R-16: con el default de 1 s del TimeLimiter, esta llamada de 1,5 s caería al fallback.
	@Test
	void fanIdentityCircuitBreakerAllowsCallsUpToThreeSeconds() {
		String result = circuitBreakerFactory.create(FanIdentityLookupService.CIRCUIT_BREAKER_NAME)
				.run(() -> slowCall(Duration.ofMillis(1500)), throwable -> "fallback");

		assertEquals("ok", result);
	}

	private static String slowCall(Duration duration) {
		try {
			Thread.sleep(duration);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		return "ok";
	}

}
