package com.nedbank.avo.assessor.vision;

import com.nedbank.avo.assessor.config.OpenAiProperties;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiChatVisionClient implements OpenAiVisionClient {
	private static final String UNKNOWN_VALUE = "unknown";
	private static final String DEFAULT_IMAGE_CONTENT_TYPE = "image/jpeg";
	private static final String ANALYSIS_IMAGE_CONTENT_TYPE = "image/png";
	private static final String SYSTEM_JSON_ONLY_MESSAGE = "Return only valid JSON without markdown or commentary.";
	private static final String OPEN_AI_ORGANIZATION_HEADER = "OpenAI-Organization";
	private static final String IMAGE_EDIT_URI = "/v1/images/edits";
	private static final String CHAT_COMPLETIONS_URI = "/v1/chat/completions";

	private final WebClient webClient;
	private final ObjectMapper objectMapper;
	private final OpenAiProperties properties;
	private final VehicleImageReader imageReader;

	public OpenAiChatVisionClient(
		WebClient.Builder webClientBuilder,
		ObjectMapper objectMapper,
		OpenAiProperties properties,
		VehicleImageReader imageReader
	) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.imageReader = imageReader;
		WebClient.Builder builder = webClientBuilder
			.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + safeApiKey(properties.apiKey()))
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
		if (properties.organization() != null && !properties.organization().isBlank()) {
			builder.defaultHeader(OPEN_AI_ORGANIZATION_HEADER, properties.organization());
		}
		this.webClient = builder.build();
	}

	@Override
	public VehicleDetailsResult extractVehicleDetails(String contentType, byte[] licenseDiscImage) {
		VisionPromptResponse visionPromptResponse = executeVisionPrompt(
			properties.model(),
			buildVehicleDetailsPrompt(),
			contentType,
			licenseDiscImage
		);
		VehicleDetails vehicleDetails = parseVehicleDetails(visionPromptResponse.content());
		return new VehicleDetailsResult(vehicleDetails, visionPromptResponse.totalTokens());
	}

	@Override
	public DamageAnalysisResult analyzeVehicleDamage(VehicleImageAngle angle, VehicleDetails vehicleDetails, String contentType, byte[] vehicleImage) {
		BufferedImage sourceImage = imageReader.read(vehicleImage);
		byte[] analysisImageBytes = toPngBytes(sourceImage);
		if (angle == VehicleImageAngle.ODOMETER) {
			return extractOdometerReading(analysisImageBytes);
		}
		DamageFindingsExtractionResult findingsExtractionResult = extractDamageFindings(angle, vehicleDetails, analysisImageBytes);
		byte[] annotatedBytes = editImage(angle, vehicleDetails, analysisImageBytes, findingsExtractionResult.findings());
		return new DamageAnalysisResult(
			"",
			findingsExtractionResult.totalTokens(),
			false,
			ANALYSIS_IMAGE_CONTENT_TYPE,
			annotatedBytes,
			findingsExtractionResult.findings()
		);
	}

	private DamageFindingsExtractionResult extractDamageFindings(VehicleImageAngle angle, VehicleDetails vehicleDetails, byte[] vehicleImageBytes) {
		VehicleContext vehicleContext = buildVehicleContext(vehicleDetails);

		VisionPromptResponse visionPromptResponse = executeVisionPrompt(
			properties.model(),
			buildDamageFindingsPrompt(vehicleContext.modelYear(), vehicleContext.make(), vehicleContext.model(), angle),
			ANALYSIS_IMAGE_CONTENT_TYPE,
			vehicleImageBytes
		);

		List<DamageFindingResult> findings = parseDamageFindings(visionPromptResponse.content());
		return new DamageFindingsExtractionResult(findings, visionPromptResponse.totalTokens());
	}

	private DamageAnalysisResult extractOdometerReading(byte[] odometerImageBytes) {
		VisionPromptResponse visionPromptResponse = executeVisionPrompt(
			properties.model(),
			buildOdometerPrompt(),
			ANALYSIS_IMAGE_CONTENT_TYPE,
			odometerImageBytes
		);
		String reading = parseOdometerReading(visionPromptResponse.content());
		return new DamageAnalysisResult(
			reading,
			visionPromptResponse.totalTokens(),
			false,
			ANALYSIS_IMAGE_CONTENT_TYPE,
			odometerImageBytes,
			List.of()
		);
	}

	private byte[] editImage(
		VehicleImageAngle angle,
		VehicleDetails vehicleDetails,
		byte[] imageBytes,
		List<DamageFindingResult> findings
	) {
		ensureOpenAiConfigured();
		VehicleContext vehicleContext = buildVehicleContext(vehicleDetails);
		String findingsForPrompt = formatFindingsForEditPrompt(findings);

		String prompt = buildEditPrompt(
			vehicleContext.colour(),
			vehicleContext.modelYear(),
			vehicleContext.make(),
			vehicleContext.model(),
			angle,
			findingsForPrompt);

		MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
		bodyBuilder.part("model", properties.imageEditModel());
		bodyBuilder.part("prompt", prompt);
		bodyBuilder.part("image", imageBytes)
				.filename("vehicle.png")
				.contentType(MediaType.IMAGE_PNG);

		JsonNode rootNode = webClient.post()
				.uri(IMAGE_EDIT_URI)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.bodyValue(bodyBuilder.build())
				.retrieve()
				.bodyToMono(JsonNode.class)
				.block();

		if (rootNode == null) {
			throw new OpenAiIntegrationException("OpenAI image edit API did not return a response body");
		}

		JsonNode b64Node = rootNode.path("data").path(0).path("b64_json");
		if (b64Node.isMissingNode() || b64Node.isNull()) {
			throw new OpenAiIntegrationException("OpenAI image edit response did not contain annotated image data");
		}

		return decodeImagePayload(b64Node);
	}

	private VisionPromptResponse executeVisionPrompt(String model, String prompt, String contentType, byte[] imageBytes) {
		ensureOpenAiConfigured();

		Map<String, Object> imageUrl = Map.of(
			"url",
			"data:%s;base64,%s".formatted(normalizeContentType(contentType), java.util.Base64.getEncoder().encodeToString(imageBytes)));
		Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
		Map<String, Object> imagePart = Map.of("type", "image_url", "image_url", imageUrl);
		Map<String, Object> systemMessage = Map.of("role", "system", "content", SYSTEM_JSON_ONLY_MESSAGE);
		Map<String, Object> userMessage = Map.of("role", "user", "content", List.of(textPart, imagePart));
		Map<String, Object> responseFormat = Map.of("type", "json_object");

		Map<String, Object> requestBody = buildChatCompletionRequest(model, systemMessage, userMessage, responseFormat);

		JsonNode rootNode = webClient.post()
			.uri(CHAT_COMPLETIONS_URI)
			.bodyValue(requestBody)
			.retrieve()
			.bodyToMono(JsonNode.class)
			.block();

		if (rootNode == null) {
			throw new OpenAiIntegrationException("OpenAI did not return a response body");
		}

		JsonNode contentNode = rootNode.path("choices").path(0).path("message").path("content");
		if (contentNode.isMissingNode() || contentNode.isNull()) {
			throw new OpenAiIntegrationException("OpenAI response did not contain a message body");
		}

		long totalTokens = rootNode.path("usage").path("total_tokens").asLong(0);
		try {
			String content = objectMapper.treeToValue(contentNode, String.class);
			if (content == null) {
				throw new OpenAiIntegrationException("OpenAI response message body was empty");
			}
			return new VisionPromptResponse(content, totalTokens);
		} catch (JacksonException exception) {
			throw new OpenAiIntegrationException("Failed to parse OpenAI response message body", exception);
		}
	}

	private void ensureOpenAiConfigured() {
		if (!properties.enabled()) {
			throw new OpenAiIntegrationException("OpenAI integration is disabled. Set app.openai.enabled=true to enable assessment analysis.");
		}
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new OpenAiIntegrationException("OpenAI API key is not configured. Set app.openai.api-key before calling the API.");
		}
	}

	private String normalizeContentType(String contentType) {
		return contentType == null || contentType.isBlank() ? DEFAULT_IMAGE_CONTENT_TYPE : contentType;
	}

	private String safeApiKey(String apiKey) {
		return apiKey == null ? "" : apiKey;
	}

	private String blankToEmpty(String value) {
		return value == null ? "" : value;
	}

	private String valueOrUnknown(String value) {
		return value == null || value.isBlank() ? UNKNOWN_VALUE : value;
	}

	private VehicleDetails parseVehicleDetails(String payload) {
		try {
			LicenseDiscResponse response = objectMapper.readValue(payload, LicenseDiscResponse.class);
			return new VehicleDetails(
				blankToEmpty(response.registrationNumber),
				blankToEmpty(response.make),
				blankToEmpty(response.model),
				blankToEmpty(response.modelYear),
				blankToEmpty(response.colour),
				blankToEmpty(response.engineNumber),
				blankToEmpty(response.vinNumber),
				blankToEmpty(response.vehicleCategory),
				blankToEmpty(response.expiryDate),
				response.extractedFields == null ? Map.of() : response.extractedFields);
		} catch (JacksonException exception) {
			throw new OpenAiIntegrationException("OpenAI returned invalid vehicle detail JSON", exception);
		}
	}

	private List<DamageFindingResult> parseDamageFindings(String payload) {
		try {
			DamageFindingsResponse response = objectMapper.readValue(payload, DamageFindingsResponse.class);
			List<DamageFindingResult> findings = new ArrayList<>();
			if (response.findings == null) {
				return findings;
			}
			for (DamageFindingResponse finding : response.findings) {
				if (finding == null) {
					continue;
				}
				findings.add(new DamageFindingResult(
					blankToEmpty(finding.category),
					blankToEmpty(finding.severity),
					blankToEmpty(finding.summary),
					blankToEmpty(finding.panel),
					blankToEmpty(finding.recommendedAction)));
			}
			return findings;
		} catch (JacksonException exception) {
			throw new OpenAiIntegrationException("OpenAI returned invalid damage findings JSON", exception);
		}
	}

	private String parseOdometerReading(String payload) {
		try {
			OdometerResponse response = objectMapper.readValue(payload, OdometerResponse.class);
			return blankToEmpty(response.odometerReading);
		} catch (JacksonException exception) {
			throw new OpenAiIntegrationException("OpenAI returned invalid odometer JSON", exception);
		}
	}

	private Map<String, Object> buildChatCompletionRequest(
		String model,
		Map<String, Object> systemMessage,
		Map<String, Object> userMessage,
		Map<String, Object> responseFormat) {
		return Map.of(
			"model", model,
			"messages", List.of(systemMessage, userMessage),
			"response_format", responseFormat);
	}

	private byte[] decodeImagePayload(JsonNode b64Node) {
		String b64;
		try {
			b64 = objectMapper.treeToValue(b64Node, String.class);
		} catch (JacksonException exception) {
			throw new OpenAiIntegrationException("Failed to read annotated image data from OpenAI response", exception);
		}
		if (b64 == null) {
			throw new OpenAiIntegrationException("OpenAI image edit response contained an empty image payload");
		}
		int commaIndex = b64.indexOf(',');
		if (commaIndex >= 0) {
			b64 = b64.substring(commaIndex + 1);
		}
		return java.util.Base64.getDecoder().decode(b64);
	}

	private VehicleContext buildVehicleContext(VehicleDetails vehicleDetails) {
		if (vehicleDetails == null) {
			return new VehicleContext(UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE, UNKNOWN_VALUE);
		}
		return new VehicleContext(
			valueOrUnknown(vehicleDetails.make()),
			valueOrUnknown(vehicleDetails.model()),
			valueOrUnknown(vehicleDetails.modelYear()),
			valueOrUnknown(vehicleDetails.colour()));
	}

	private String formatFindingsForEditPrompt(List<DamageFindingResult> findings) {
		if (findings == null || findings.isEmpty()) {
			return "(none)";
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < findings.size(); i++) {
			DamageFindingResult finding = findings.get(i);
			builder.append(i + 1)
				.append(". panel=")
				.append(valueOrUnknown(finding.panel()))
				.append(" | category=")
				.append(valueOrUnknown(finding.category()))
				.append(" | severity=")
				.append(valueOrUnknown(finding.severity()))
				.append(" | recommendedAction=")
				.append(valueOrUnknown(finding.recommendedAction()))
				.append(" | summary=")
				.append(valueOrUnknown(finding.summary()));
			if (i < findings.size() - 1) {
				builder.append('\n');
			}
		}
		return builder.toString();
	}

	private String buildVehicleDetailsPrompt() {
		return """
		You are assisting an insurance vehicle assessor. Extract all vehicle details you can identify from the supplied license disc image and the provided VIN number.
		Respond with JSON only using this shape:
		{
		  "registrationNumber": "",
		  "make": "",
		  "model": "",
		  "modelYear": "",
		  "colour": "",
		  "engineNumber": "",
		  "vinNumber": "",
		  "vehicleCategory": "",
		  "expiryDate": "",
		  "extractedFields": {
		    "fieldName": "fieldValue"
		  }
		}
		Leave unknown values as empty strings.
		""";
	}

	private String buildDamageFindingsPrompt(String modelYear, String make, String model, VehicleImageAngle angle) {
		return """
		You are an expert vehicle damage assessor doing a deep and careful inspection.
		Analyze this %s %s %s image from the %s angle and return JSON only in this exact shape:
		{
		  "findings": [
		    {
		      "category": "",
		      "severity": "",
		      "summary": "",
			      "panel": "",
			      "recommendedAction": ""
		    }
		  ]
		}
		Rules:
		- Inspect the whole visible vehicle area in detail before returning findings.
		- Include subtle damage when clearly visible (light scratches, chips, hairline cracks, shallow dents).
		- Include only visible vehicle damage findings.
		- If there is no visible damage, return an empty findings array.
		- Keep summary concise (max 20 words).
		- Use lowercase severity values: minor, moderate, severe.
		- The panel field must be specific (for example: front bumper left, front-left fender, rear-right door).
		- Use lowercase recommendedAction values: fix or replace.
		- Choose replace for severe structural damage, torn metal/plastic, or broken parts; otherwise use fix.
		""".formatted(modelYear, make, model, angle);
	}

	private String buildOdometerPrompt() {
		return """
		You are assisting an insurance assessor.
		Read the odometer value from this dashboard image and return JSON only in this exact shape:
		{
		  "odometerReading": ""
		}
		Rules:
		- Return only digits for the reading (no units, commas, spaces, or punctuation).
		- If the reading is unclear or not visible, return an empty string.
		""";
	}

	private String buildEditPrompt(String colour, String modelYear, String make, String model, VehicleImageAngle angle, String findingsForPrompt) {
		return """
You are an expert insurance vehicle damage assessor annotating a %s %s %s %s for a claim.
This image shows the %s view of the vehicle.

You MUST use the numbered findings list below as the source of truth for what to annotate.
Findings list:
%s

## OBJECTIVE
Draw one box for each finding in the provided numbered list.
Each box must surround the reported damage area (including dents where present) and map to the same number.

## ANNOTATION RULES
For each numbered finding from the provided list, draw a rectangle directly on the ORIGINAL image:
- Color: pure bright red (#FF0000)
- Border thickness: 5–7 px, solid line, NO fill (transparent inside)
- Add a thin white outline just outside the red border so boxes stay visible on
  both light (silver) and dark areas of the car
- Place the matching number from your list in the top-left corner of each box,
  in bright red on a small white background, so each box maps to your list
- Size each box tightly around the damage area with a small margin, but never so tight
  that the border covers the damage itself
- If two defects are very close, draw two separate small boxes rather than one
  large box
- If a listed finding cannot be located confidently, skip boxing that finding rather than guessing
- If no findings were provided, return the original image unchanged

## DO NOT MARK
Vehicle design features, panel lines, shadows, reflections, dirt/mud, water spots,
or the license plate. If unsure whether a mark is damage vs. a reflection, only
mark it if it is still visible at high magnification and does not move with the
light/angle.

## CRITICAL — IMAGE HANDLING
Do NOT regenerate, redraw, repaint, restyle or recreate the vehicle or background.
Keep the original photo 100 percent unchanged and ONLY overlay the red rectangles and
their numbers on top of it. The output must be the original image with markers
added — nothing else altered.

## SELF-CHECK BEFORE RETURNING
Verify that each box number matches a finding number and that each box is over the corresponding damage region.
Then return only the edited image.
		""".formatted(colour, modelYear, make, model, angle, findingsForPrompt);
	}

	private byte[] toPngBytes(BufferedImage image) {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			if (!javax.imageio.ImageIO.write(image, "png", outputStream)) {
				throw new OpenAiIntegrationException("Failed to normalize vehicle image for analysis");
			}
			return outputStream.toByteArray();
		} catch (IOException exception) {
			throw new OpenAiIntegrationException("Failed to normalize vehicle image for analysis", exception);
		}
	}

	private static final class LicenseDiscResponse {
		public String registrationNumber;
		public String make;
		public String model;
		public String modelYear;
		public String colour;
		public String engineNumber;
		public String vinNumber;
		public String vehicleCategory;
		public String expiryDate;
		public Map<String, String> extractedFields;
	}

	private static final class OdometerResponse {
		public String odometerReading;
	}

	private static final class DamageFindingsResponse {
		public List<DamageFindingResponse> findings;
	}

	private static final class DamageFindingResponse {
		public String category;
		public String severity;
		public String summary;
		public String panel;
		public String recommendedAction;
	}

	private record DamageFindingsExtractionResult(List<DamageFindingResult> findings, long totalTokens) {
	}

	private record VisionPromptResponse(String content, long totalTokens) {
	}

	private record VehicleContext(String make, String model, String modelYear, String colour) {
	}
}
