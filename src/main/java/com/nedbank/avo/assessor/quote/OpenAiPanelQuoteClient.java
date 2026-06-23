package com.nedbank.avo.assessor.quote;

import com.nedbank.avo.assessor.config.OpenAiProperties;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OpenAiPanelQuoteClient {

	private final WebClient webClient;
	private final ObjectMapper objectMapper;
	private final OpenAiProperties properties;

	public OpenAiPanelQuoteClient(WebClient.Builder webClientBuilder, ObjectMapper objectMapper, OpenAiProperties properties) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		WebClient.Builder builder = webClientBuilder
			.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + safeApiKey(properties.apiKey()))
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		if (properties.organization() != null && !properties.organization().isBlank()) {
			builder.defaultHeader("OpenAI-Organization", properties.organization());
		}
		this.webClient = builder.build();
	}

	public QuoteLookupResult lookupPanelPrices(VehicleDetails vehicleDetails, List<PanelRequest> panelsToReplace) {
		ensureOpenAiConfigured();
		if (panelsToReplace == null || panelsToReplace.isEmpty()) {
			return new QuoteLookupResult(List.of(), 0, properties.model(), "No replace panels detected.");
		}

		Map<String, Object> responseFormat = Map.of("type", "json_object");
		Map<String, Object> systemMessage = Map.of(
			"role", "system",
			"content", "Return only valid JSON with no markdown and no extra text."
		);
		Map<String, Object> userMessage = Map.of(
			"role", "user",
			"content", buildPanelPricingPrompt(vehicleDetails, panelsToReplace)
		);
		Map<String, Object> requestBody = Map.of(
			"model", properties.model(),
			"messages", List.of(systemMessage, userMessage),
			"response_format", responseFormat
		);

		JsonNode rootNode = webClient.post()
			.uri("/v1/chat/completions")
			.bodyValue(requestBody)
			.retrieve()
			.bodyToMono(JsonNode.class)
			.block();

		if (rootNode == null) {
			throw new OpenAiIntegrationException("OpenAI did not return a response body for quote lookup");
		}

		JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
		if (contentNode.isMissingNode() || contentNode.isNull()) {
			throw new OpenAiIntegrationException("OpenAI response did not contain quote content");
		}

		long totalTokens = rootNode.path("usage").path("total_tokens").asLong(0);

		try {
			String payload = objectMapper.treeToValue(contentNode, String.class);
			PanelQuoteResponse response = objectMapper.readValue(payload, PanelQuoteResponse.class);
			List<QuotedPanelProduct> products = new ArrayList<>();
			if (response.items != null) {
				for (PanelQuoteItem item : response.items) {
					if (item == null) {
						continue;
					}
					products.add(new QuotedPanelProduct(
						normalizePanelKey(item.panelKey),
						blankToEmpty(item.panelLabel),
						blankToEmpty(item.productName),
						blankToEmpty(item.productDescription),
						blankToEmpty(item.productUrl),
						blankToEmpty(item.productImageUrl),
						blankToEmpty(item.sourceWebsite),
						Math.max(0.0, item.panelPriceZar),
						Math.max(0.0, item.hardwarePriceZar),
						item.quantity <= 0 ? 1 : item.quantity,
						item.includedHardware == null ? List.of() : item.includedHardware,
						blankToEmpty(item.notes)
					));
				}
			}
			String notes = response.notes == null ? "" : response.notes.trim();
			return new QuoteLookupResult(products, totalTokens, properties.model(), notes);
		} catch (JacksonException exception) {
			throw new OpenAiIntegrationException("OpenAI returned invalid quote JSON", exception);
		}
	}

	private String buildPanelPricingPrompt(VehicleDetails vehicleDetails, List<PanelRequest> panelsToReplace) {
		StringBuilder requestedPanels = new StringBuilder();
		for (PanelRequest panel : panelsToReplace) {
			requestedPanels.append("- panelKey=")
				.append(panel.panelKey())
				.append(", panelLabel=")
				.append(panel.panelLabel())
				.append('\n');
		}

		String vehicleContext = "unknown vehicle";
		if (vehicleDetails != null) {
			vehicleContext = String.format(
				Locale.ROOT,
				"registration=%s, make=%s, model=%s, year=%s",
				blankToEmpty(vehicleDetails.registrationNumber()),
				blankToEmpty(vehicleDetails.make()),
				blankToEmpty(vehicleDetails.model()),
				blankToEmpty(vehicleDetails.modelYear())
			);
		}

		return """
		You are preparing a South African vehicle parts replacement quote.
		Vehicle details: %s
		Panels that need replacement (do not duplicate panels):
		%s

		Search for each panel part on South African websites.
		Preferred sources first:
		1) https://www.bdspares.co.za/
		2) https://aceauto.co.za/
		If unavailable there, use another South African website.

		For every panel include screws/clamps/fasteners needed to fit that panel.
		Return prices in ZAR only.

		Return JSON with this exact shape:
		{
		  "items": [
		    {
		      "panelKey": "",
		      "panelLabel": "",
		      "productName": "",
		      "productDescription": "",
		      "productUrl": "",
		      "productImageUrl": "",
		      "sourceWebsite": "",
		      "panelPriceZar": 0,
		      "hardwarePriceZar": 0,
		      "quantity": 1,
		      "includedHardware": ["screws", "clamps"],
		      "notes": ""
		    }
		  ],
		  "notes": ""
		}

		Rules:
		- Return at most one item per panelKey.
		- Keep productUrl and productImageUrl. The full URL's.
		- If an exact match is unavailable, choose the closest available panel for the same vehicle range and explain in notes.
		- If no reliable supplier exists for a panel, still return the item with best estimate and set notes accordingly.
		- Never include suppliers outside South Africa.
		""".formatted(vehicleContext, requestedPanels);
	}

	private String normalizePanelKey(String panelKey) {
		if (panelKey == null) {
			return "";
		}
		return panelKey.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
	}

	private void ensureOpenAiConfigured() {
		if (!properties.enabled()) {
			throw new OpenAiIntegrationException("OpenAI integration is disabled. Set app.openai.enabled=true to enable quote generation.");
		}
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new OpenAiIntegrationException("OpenAI API key is not configured. Set app.openai.api-key before generating quotes.");
		}
	}

	private String safeApiKey(String apiKey) {
		return apiKey == null ? "" : apiKey;
	}

	private String blankToEmpty(String value) {
		return value == null ? "" : value;
	}

	public record PanelRequest(String panelKey, String panelLabel) {
	}

	public record QuotedPanelProduct(
		String panelKey,
		String panelLabel,
		String productName,
		String productDescription,
		String productUrl,
		String productImageUrl,
		String sourceWebsite,
		double panelPriceZar,
		double hardwarePriceZar,
		int quantity,
		List<String> includedHardware,
		String notes
	) {
	}

	public record QuoteLookupResult(
		List<QuotedPanelProduct> items,
		long tokensUsed,
		String model,
		String notes
	) {
	}

	private static class PanelQuoteResponse {
		public List<PanelQuoteItem> items;
		public String notes;
	}

	private static class PanelQuoteItem {
		public String panelKey;
		public String panelLabel;
		public String productName;
		public String productDescription;
		public String productUrl;
		public String productImageUrl;
		public String sourceWebsite;
		public double panelPriceZar;
		public double hardwarePriceZar;
		public int quantity;
		public List<String> includedHardware;
		public String notes;
	}
}
