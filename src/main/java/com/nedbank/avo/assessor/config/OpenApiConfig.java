package com.nedbank.avo.assessor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OpenApiConfig {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper()
			.registerModule(new JavaTimeModule())
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	@Bean
	public WebClient.Builder webClientBuilder(
		@Value("${app.openai.max-in-memory-size-bytes:49999999}") int maxInMemorySizeBytes
	) {
		ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxInMemorySizeBytes))
			.build();
		return WebClient.builder().exchangeStrategies(exchangeStrategies);
	}

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("Auto Assessor API")
				.description("Vehicle damage assessment service powered by AI vision")
				.version("1.0.0")
				.contact(new Contact()
					.name("Nedbank Avo")
					.email("avo@nedbank.co.za")));
	}
}
