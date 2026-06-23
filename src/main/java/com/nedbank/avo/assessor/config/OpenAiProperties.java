package com.nedbank.avo.assessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public record OpenAiProperties(
	String apiKey,
	String baseUrl,
	String model,
	String imageEditModel,
	String organization,
	boolean enabled
) {
	public OpenAiProperties {
		baseUrl = hasText(baseUrl) ? baseUrl : "https://api.openai.com";
		model = hasText(model) ? model : "gpt-4.1-mini";
		imageEditModel = hasText(imageEditModel) ? imageEditModel : "gpt-image-1";
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}