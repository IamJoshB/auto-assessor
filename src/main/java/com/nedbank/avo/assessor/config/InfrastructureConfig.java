package com.nedbank.avo.assessor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Clock;

@Configuration
public class InfrastructureConfig {

	@Bean
	public Clock systemClock() {
		return Clock.systemUTC();
	}

	@Bean
	public HttpClient httpClient() {
		return HttpClient.newHttpClient();
	}
}
