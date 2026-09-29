package com.tpnube.alert_dispatcher_service;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.FeignClientFactory;
import org.springframework.cloud.openfeign.FeignClientProperties;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AlertDispatcherServiceApplicationTests {

	private final FeignClientProperties feignClientProperties;
	private final FeignClientFactory feignClientFactory;

	@Autowired
	AlertDispatcherServiceApplicationTests(FeignClientProperties feignClientProperties,
										   FeignClientFactory feignClientFactory) {
		this.feignClientProperties = feignClientProperties;
		this.feignClientFactory = feignClientFactory;
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

}
