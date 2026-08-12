package com.tpnube.alert_dispatcher_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AlertDispatcherServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlertDispatcherServiceApplication.class, args);
	}

}
