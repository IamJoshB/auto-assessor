package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import com.nedbank.avo.assessor.storage.S3ImageStorageService;
import com.nedbank.avo.assessor.storage.StoredImage;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VehicleAssessmentReportService {
	private static final String IMAGE_JPEG_CONTENT_TYPE = "image/jpeg";
	private static final String NO_DAMAGE_SUMMARY_ROW = "<tr><td colspan=\"6\">No damage findings detected.</td></tr>";
	private static final String NO_DAMAGE_IMAGE_CARD = "<div class=\"empty-state\">No damage images to display.</div>";
	private static final String NO_VEHICLE_DETAILS_ROW = "<tr><td colspan=\"2\">No vehicle details available.</td></tr>";
	private static final String NO_EXTRACTED_FIELDS_ROW = "<tr><td colspan=\"2\">No additional extracted fields available.</td></tr>";

	private static final DateTimeFormatter REPORT_TIMESTAMP_FORMAT = DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
		.withZone(ZoneId.of("UTC"));

	private static final List<String> PANEL_ORDER = List.of(
		"bonnet",
		"roof",
		"boot",
		"front_bumper",
		"rear_bumper",
		"front_left_fender",
		"front_right_fender",
		"rear_left_quarter",
		"rear_right_quarter",
		"front_left_door",
		"rear_left_door",
		"front_right_door",
		"rear_right_door",
		"left_side_mirror",
		"right_side_mirror",
		"windscreen"
	);

	private static final Map<String, String> PANEL_LABELS = Map.ofEntries(
		Map.entry("bonnet", "Bonnet"),
		Map.entry("roof", "Roof"),
		Map.entry("boot", "Boot"),
		Map.entry("front_bumper", "Front Bumper"),
		Map.entry("rear_bumper", "Rear Bumper"),
		Map.entry("front_left_fender", "Front Left Fender"),
		Map.entry("front_right_fender", "Front Right Fender"),
		Map.entry("rear_left_quarter", "Rear Left Quarter"),
		Map.entry("rear_right_quarter", "Rear Right Quarter"),
		Map.entry("front_left_door", "Front Left Door"),
		Map.entry("rear_left_door", "Rear Left Door"),
		Map.entry("front_right_door", "Front Right Door"),
		Map.entry("rear_right_door", "Rear Right Door"),
		Map.entry("left_side_mirror", "Left Side Mirror"),
		Map.entry("right_side_mirror", "Right Side Mirror"),
		Map.entry("windscreen", "Windscreen")
	);

	private final S3ImageStorageService s3ImageStorageService;
	private final Clock clock;

	public VehicleAssessmentReportService(S3ImageStorageService s3ImageStorageService, Clock clock) {
		this.s3ImageStorageService = s3ImageStorageService;
		this.clock = clock;
	}

	public boolean shouldGenerateReport(Assessment assessment) {
		if (assessment == null) {
			return false;
		}
		if (assessment.getReportPdfUrl() != null && !assessment.getReportPdfUrl().isBlank()) {
			return false;
		}
		return AssessmentImageCaptureRules.hasAllRequiredSuccessfulImages(assessment);
	}

	public GeneratedReportPaths generateAndStoreReport(Assessment assessment) {
		String template = loadTemplate("vehicle-assessment-report-template.html");
		String html = populateTemplate(template, assessment);

		StoredImage htmlReport = s3ImageStorageService.storeAssessmentReportHtml(assessment.getId(), html);
		byte[] pdfBytes = htmlToPdf(html);
		StoredImage pdfReport = s3ImageStorageService.storeAssessmentReportPdf(assessment.getId(), pdfBytes);
		return new GeneratedReportPaths(htmlReport.url(), pdfReport.url());
	}

	private String loadTemplate(String fileName) {
		try {
			ClassPathResource classPathResource = new ClassPathResource(fileName);
			byte[] bytes = classPathResource.getInputStream().readAllBytes();
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new OpenAiIntegrationException("Failed to load assessment report template", exception);
		}
	}

	private String populateTemplate(String template, Assessment assessment) {
		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("{{ASSESSMENT_ID}}", escapeHtml(assessment.getId()));
		placeholders.put("{{GENERATED_AT}}", REPORT_TIMESTAMP_FORMAT.format(Instant.now(clock)));
		placeholders.put("{{TOTAL_TOKENS}}", String.valueOf(assessment.getTotalTokensUsed()));
		long totalAnalysisDurationMs = assessment.getImages().stream()
			.mapToLong(image -> image.analysisDurationMs() == null ? 0L : image.analysisDurationMs())
			.sum();
		placeholders.put("{{TOTAL_ANALYSIS_TIME}}", formatDuration(totalAnalysisDurationMs));
		placeholders.put("{{ODOMETER_READING}}", escapeHtml(trimToDash(assessment.getOdometerReading())));
		placeholders.put("{{VEHICLE_DATA_ROWS}}", buildVehicleDataRows(assessment.getVehicleDetails()));
		placeholders.put("{{EXTRACTED_FIELDS_ROWS}}", buildExtractedFieldsRows(assessment.getVehicleDetails()));
		placeholders.put("{{DAMAGE_SUMMARY_ROWS}}", buildDamageSummaryRows(assessment.getImages()));
		placeholders.put("{{DAMAGE_IMAGE_CARDS}}", buildDamageImageCards(assessment.getImages()));
		placeholders.put("{{HIGHLIGHTED_PANELS_SVG}}", buildHighlightedPanelsSvg(assessment.getImages()));

		String html = template;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			html = html.replace(entry.getKey(), entry.getValue());
		}
		return html;
	}

	private String buildVehicleDataRows(VehicleDetails vehicleDetails) {
		if (vehicleDetails == null) {
			return NO_VEHICLE_DETAILS_ROW;
		}

		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Registration Number", vehicleDetails.registrationNumber());
		rows.put("Make", vehicleDetails.make());
		rows.put("Model", vehicleDetails.model());
		rows.put("Model Year", vehicleDetails.modelYear());
		rows.put("Colour", vehicleDetails.colour());
		rows.put("Engine Number", vehicleDetails.engineNumber());
		rows.put("VIN", vehicleDetails.vinNumber());
		rows.put("Category", vehicleDetails.vehicleCategory());
		rows.put("Disc Expiry", vehicleDetails.expiryDate());

		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> row : rows.entrySet()) {
			builder.append("<tr><th>")
				.append(escapeHtml(row.getKey()))
				.append("</th><td>")
				.append(escapeHtml(trimToDash(row.getValue())))
				.append("</td></tr>");
		}
		return builder.toString();
	}

	private String buildExtractedFieldsRows(VehicleDetails vehicleDetails) {
		if (vehicleDetails == null || vehicleDetails.extractedFields() == null || vehicleDetails.extractedFields().isEmpty()) {
			return NO_EXTRACTED_FIELDS_ROW;
		}

		return vehicleDetails.extractedFields().entrySet().stream()
			.sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
			.map(entry -> "<tr><th>" + escapeHtml(entry.getKey()) + "</th><td>" + escapeHtml(trimToDash(entry.getValue())) + "</td></tr>")
			.collect(Collectors.joining());
	}

	private String buildDamageSummaryRows(List<AssessmentImage> images) {
		List<DamageRow> rows = collectDamageRows(images);
		if (rows.isEmpty()) {
			return NO_DAMAGE_SUMMARY_ROW;
		}

		StringBuilder builder = new StringBuilder();
		for (DamageRow row : rows) {
			builder.append("<tr><td>")
				.append(escapeHtml(row.angle))
				.append("</td><td>")
				.append(escapeHtml(row.panel))
				.append("</td><td>")
				.append(escapeHtml(row.category))
				.append("</td><td><span class=\"severity ")
				.append(severityClass(row.severity))
				.append("\">")
				.append(escapeHtml(row.severity))
				.append("</span></td><td>")
				.append(escapeHtml(row.recommendedAction))
				.append("</td><td>")
				.append(escapeHtml(row.summary))
				.append("</td></tr>");
		}
		return builder.toString();
	}

	private String buildDamageImageCards(List<AssessmentImage> images) {
		List<AssessmentImage> withFindings = successfulImagesWithFindings(images);

		if (withFindings.isEmpty()) {
			return NO_DAMAGE_IMAGE_CARD;
		}

		StringBuilder builder = new StringBuilder();
		for (AssessmentImage image : withFindings) {
			String imageSrc = toDataUri(image.vehicleImageUrl(), image.vehicleImageContentType());
			String findingsList = image.findings().stream()
				.map(finding -> "<li><b>" + escapeHtml(trimToDash(finding.panel())) + "</b>: " + escapeHtml(trimToDash(finding.summary())) + "</li>")
				.collect(Collectors.joining());
			builder.append("<section class=\"damage-card\"><div class=\"damage-card-header\"><h3>")
				.append(escapeHtml(formatAngle(image.angle())))
				.append("</h3></div><img class=\"damage-image\" src=\"")
				.append(imageSrc)
				.append("\" alt=\"Damage image for ")
				.append(escapeHtml(formatAngle(image.angle())))
				.append("\"/><ul>")
				.append(findingsList)
				.append("</ul></section>");
		}
		return builder.toString();
	}

	private List<AssessmentImage> successfulImagesWithFindings(List<AssessmentImage> images) {
		return images.stream()
			.filter(image -> image.status() == ImageAnalysisStatus.SUCCESS)
			.filter(image -> image.findings() != null && !image.findings().isEmpty())
			.sorted(Comparator.comparing(image -> image.angle().name()))
			.toList();
	}

	private String buildHighlightedPanelsSvg(List<AssessmentImage> images) {
		Set<String> highlightedPanels = collectDamageRows(images).stream()
			.map(row -> normalizePanel(row.panel))
			.filter(panel -> !panel.isBlank())
			.collect(Collectors.toSet());

		StringBuilder panelLegend = new StringBuilder();
		for (String panelKey : PANEL_ORDER) {
			panelLegend.append("<div class=\"legend-item\"><span class=\"legend-chip")
				.append(highlightedPanels.contains(panelKey) ? " active\"></span>" : "\"></span>")
				.append(escapeHtml(PANEL_LABELS.getOrDefault(panelKey, panelKey)))
				.append("</div>");
		}

		return """
			<div class=\"panel-visual\">
			  <svg viewBox=\"0 0 820 330\" xmlns=\"http://www.w3.org/2000/svg\" role=\"img\" aria-label=\"Vehicle panel map\">
			    <rect x=\"40\" y=\"120\" width=\"740\" height=\"120\" rx=\"28\" class=\"panel %s\"/>
			    <rect x=\"300\" y=\"85\" width=\"220\" height=\"40\" rx=\"12\" class=\"panel %s\"/>
			    <rect x=\"310\" y=\"245\" width=\"200\" height=\"38\" rx=\"10\" class=\"panel %s\"/>
			    <rect x=\"65\" y=\"140\" width=\"80\" height=\"80\" rx=\"10\" class=\"panel %s\"/>
			    <rect x=\"675\" y=\"140\" width=\"80\" height=\"80\" rx=\"10\" class=\"panel %s\"/>
			
			    <rect x=\"145\" y=\"135\" width=\"70\" height=\"90\" rx=\"10\" class=\"panel %s\"/>
			    <rect x=\"605\" y=\"135\" width=\"70\" height=\"90\" rx=\"10\" class=\"panel %s\"/>
			    <rect x=\"215\" y=\"135\" width=\"95\" height=\"90\" rx=\"10\" class=\"panel %s\"/>
			    <rect x=\"510\" y=\"135\" width=\"95\" height=\"90\" rx=\"10\" class=\"panel %s\"/>
			    <rect x=\"310\" y=\"135\" width=\"100\" height=\"90\" rx=\"10\" class=\"panel %s\"/>
			    <rect x=\"410\" y=\"135\" width=\"100\" height=\"90\" rx=\"10\" class=\"panel %s\"/>
			
			    <circle cx=\"225\" cy=\"165\" r=\"15\" class=\"panel %s\"/>
			    <circle cx=\"595\" cy=\"165\" r=\"15\" class=\"panel %s\"/>
			    <circle cx=\"50\" cy=\"180\" r=\"16\" class=\"panel %s\"/>
			    <circle cx=\"770\" cy=\"180\" r=\"16\" class=\"panel %s\"/>
			  </svg>
			</div>
			<div class=\"panel-legend\">%s</div>
		""".formatted(
			highlightedPanels.contains("windscreen") ? "active" : "",
			highlightedPanels.contains("roof") ? "active" : "",
			highlightedPanels.contains("boot") ? "active" : "",
			highlightedPanels.contains("front_bumper") ? "active" : "",
			highlightedPanels.contains("rear_bumper") ? "active" : "",
			highlightedPanels.contains("front_left_fender") ? "active" : "",
			highlightedPanels.contains("front_right_fender") ? "active" : "",
			highlightedPanels.contains("front_left_door") ? "active" : "",
			highlightedPanels.contains("front_right_door") ? "active" : "",
			highlightedPanels.contains("rear_left_door") ? "active" : "",
			highlightedPanels.contains("rear_right_door") ? "active" : "",
			highlightedPanels.contains("left_side_mirror") ? "active" : "",
			highlightedPanels.contains("right_side_mirror") ? "active" : "",
			highlightedPanels.contains("rear_left_quarter") ? "active" : "",
			highlightedPanels.contains("rear_right_quarter") ? "active" : "",
			panelLegend
		);
	}

	private List<DamageRow> collectDamageRows(List<AssessmentImage> images) {
		List<DamageRow> rows = new ArrayList<>();
		for (AssessmentImage image : images) {
			if (image.status() != ImageAnalysisStatus.SUCCESS || image.findings() == null || image.findings().isEmpty()) {
				continue;
			}
			for (VehicleDamageFinding finding : image.findings()) {
				rows.add(new DamageRow(
					formatAngle(image.angle()),
					trimToDash(finding.panel()),
					trimToDash(finding.category()),
					trimToDash(finding.severity()),
					trimToDash(normalizeRecommendedAction(finding.recommendedAction())),
					trimToDash(finding.summary())
				));
			}
		}
		List<DamageRow> deduplicated = deduplicateDamageRows(rows);
		deduplicated.sort(Comparator.comparing((DamageRow row) -> row.angle).thenComparing(row -> row.panel));
		return deduplicated;
	}

	private List<DamageRow> deduplicateDamageRows(List<DamageRow> rows) {
		List<DamageRow> deduplicated = new ArrayList<>();
		for (DamageRow candidate : rows) {
			boolean merged = false;
			for (int i = 0; i < deduplicated.size(); i++) {
				DamageRow existing = deduplicated.get(i);
				if (!isLikelySameDamage(existing, candidate)) {
					continue;
				}
				deduplicated.set(i, mergeDamageRows(existing, candidate));
				merged = true;
				break;
			}
			if (!merged) {
				deduplicated.add(candidate);
			}
		}
		return deduplicated;
	}

	private boolean isLikelySameDamage(DamageRow left, DamageRow right) {
		String leftPanel = normalizePanel(left.panel());
		String rightPanel = normalizePanel(right.panel());
		String leftCategory = normalizeFreeText(left.category());
		String rightCategory = normalizeFreeText(right.category());
		String leftSummary = normalizeFreeText(left.summary());
		String rightSummary = normalizeFreeText(right.summary());

		if (leftSummary.isBlank() || rightSummary.isBlank()) {
			return false;
		}

		if (leftSummary.equals(rightSummary) && leftCategory.equals(rightCategory)) {
			return true;
		}

		boolean samePanel = !leftPanel.isBlank() && leftPanel.equals(rightPanel);
		boolean sameCategory = !leftCategory.isBlank() && leftCategory.equals(rightCategory);
		if (!(samePanel && sameCategory)) {
			return false;
		}

		if (leftSummary.contains(rightSummary) || rightSummary.contains(leftSummary)) {
			return true;
		}

		double summaryOverlap = jaccardSimilarity(tokenize(leftSummary), tokenize(rightSummary));
		return summaryOverlap >= 0.7;
	}

	private DamageRow mergeDamageRows(DamageRow existing, DamageRow candidate) {
		String mergedAngle = mergeCommaSeparated(existing.angle(), candidate.angle());
		String mergedSeverity = severityRank(existing.severity()) >= severityRank(candidate.severity()) ? existing.severity() : candidate.severity();
		String mergedRecommendedAction = actionRank(existing.recommendedAction()) >= actionRank(candidate.recommendedAction())
			? existing.recommendedAction()
			: candidate.recommendedAction();
		String mergedSummary = chooseBestSummary(existing.summary(), candidate.summary());

		return new DamageRow(
			mergedAngle,
			existing.panel(),
			existing.category(),
			mergedSeverity,
			mergedRecommendedAction,
			mergedSummary
		);
	}

	private String chooseBestSummary(String first, String second) {
		String left = trimToDash(first);
		String right = trimToDash(second);
		if ("-".equals(left)) {
			return right;
		}
		if ("-".equals(right)) {
			return left;
		}
		return right.length() > left.length() ? right : left;
	}

	private String mergeCommaSeparated(String first, String second) {
		LinkedHashSet<String> values = new LinkedHashSet<>();
		for (String token : (trimToDash(first) + "," + trimToDash(second)).split(",")) {
			String value = token == null ? "" : token.trim();
			if (value.isBlank() || "-".equals(value)) {
				continue;
			}
			values.add(value);
		}
		if (values.isEmpty()) {
			return "-";
		}
		return String.join(", ", values);
	}

	private int severityRank(String severity) {
		String normalized = normalizeFreeText(severity);
		if (normalized.contains("severe") || normalized.contains("major") || normalized.contains("high")) {
			return 3;
		}
		if (normalized.contains("moderate") || normalized.contains("medium")) {
			return 2;
		}
		if (normalized.contains("minor") || normalized.contains("low")) {
			return 1;
		}
		return 0;
	}

	private String normalizeRecommendedAction(String action) {
		String normalized = normalizeFreeText(action);
		if (normalized.contains("replace")) {
			return "replace";
		}
		if (normalized.contains("fix") || normalized.contains("repair")) {
			return "fix";
		}
		return "-";
	}

	private int actionRank(String action) {
		String normalized = normalizeRecommendedAction(action);
		if ("replace".equals(normalized)) {
			return 2;
		}
		if ("fix".equals(normalized)) {
			return 1;
		}
		return 0;
	}

	private String normalizeFreeText(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}

	private Set<String> tokenize(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(value.split("\\s+"))
			.filter(token -> token.length() > 2)
			.collect(Collectors.toSet());
	}

	private double jaccardSimilarity(Set<String> left, Set<String> right) {
		if (left.isEmpty() || right.isEmpty()) {
			return 0.0;
		}
		Set<String> intersection = new HashSet<>(left);
		intersection.retainAll(right);
		Set<String> union = new HashSet<>(left);
		union.addAll(right);
		if (union.isEmpty()) {
			return 0.0;
		}
		return (double) intersection.size() / (double) union.size();
	}

	private String formatDuration(long durationMs) {
		if (durationMs < 1000) {
			return durationMs + " ms";
		}
		long seconds = durationMs / 1000;
		long milliseconds = durationMs % 1000;
		if (milliseconds == 0) {
			return seconds + " s";
		}
		return String.format("%d.%03d s", seconds, milliseconds);
	}

	private String toDataUri(String url, String contentType) {
		if (url == null || url.isBlank()) {
			return "";
		}
		byte[] imageBytes = s3ImageStorageService.loadFile(url);
		String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
		String normalizedContentType = (contentType == null || contentType.isBlank())
			? IMAGE_JPEG_CONTENT_TYPE
			: contentType;
		return "data:" + normalizedContentType + ";base64," + base64;
	}

	private byte[] htmlToPdf(String html) {
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			org.jsoup.nodes.Document jsoupDocument = Jsoup.parse(html);
			jsoupDocument.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml);
			org.w3c.dom.Document w3cDocument = new W3CDom().fromJsoup(jsoupDocument);
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withW3cDocument(w3cDocument, null);
			builder.toStream(outputStream);
			builder.run();
			return outputStream.toByteArray();
		} catch (Exception exception) {
			throw new OpenAiIntegrationException("Failed to render assessment report PDF", exception);
		}
	}

	private String normalizePanel(String panel) {
		return VehiclePanelNormalizer.normalizePanelKey(panel);
	}

	private String severityClass(String severity) {
		String value = severity == null ? "unknown" : severity.toLowerCase(Locale.ROOT);
		if (value.contains("high") || value.contains("major") || value.contains("severe")) {
			return "sev-high";
		}
		if (value.contains("medium") || value.contains("moderate")) {
			return "sev-medium";
		}
		if (value.contains("low") || value.contains("minor")) {
			return "sev-low";
		}
		return "sev-unknown";
	}

	private String formatAngle(VehicleImageAngle angle) {
		return angle.name().toLowerCase(Locale.ROOT).replace('_', ' ');
	}

	private String trimToDash(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		return value.trim();
	}

	private String escapeHtml(String value) {
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}

	private record DamageRow(
		String angle,
		String panel,
		String category,
		String severity,
		String recommendedAction,
		String summary
	) {
	}

	public record GeneratedReportPaths(String htmlUrl, String pdfUrl) {
	}
}
