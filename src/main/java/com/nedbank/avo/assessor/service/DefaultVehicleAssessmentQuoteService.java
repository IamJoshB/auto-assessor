package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.AssessmentQuote;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.QuoteLineItem;
import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.exception.AssessmentQuoteNotFoundException;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import com.nedbank.avo.assessor.quote.OpenAiPanelQuoteClient;
import com.nedbank.avo.assessor.repository.AssessmentQuoteRepository;
import com.nedbank.avo.assessor.storage.S3ImageStorageService;
import com.nedbank.avo.assessor.storage.StoredImage;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DefaultVehicleAssessmentQuoteService implements VehicleAssessmentQuoteService {
	private static final List<String> DEFAULT_HARDWARE_ITEMS = List.of("screws", "clamps");
	private static final String DEFAULT_NOTES = "Pricing sourced via AI-assisted South African supplier lookup.";
	private static final String NO_MATCH_PRODUCT_NAME = "No supplier match found";
	private static final String NO_MATCH_PRODUCT_DESCRIPTION = "No reliable South African product listing returned by AI search.";
	private static final String NO_MATCH_LINE_ITEM_NOTE = "Manual follow-up required";

	private static final DateTimeFormatter QUOTE_TIMESTAMP_FORMAT = DateTimeFormatter
		.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
		.withZone(ZoneId.of("UTC"));

	private static final double VAT_RATE = 0.15;

	private final OpenAiPanelQuoteClient openAiPanelQuoteClient;
	private final AssessmentQuoteRepository assessmentQuoteRepository;
	private final S3ImageStorageService s3ImageStorageService;
	private final Clock clock;
	private final HttpClient httpClient;

	public DefaultVehicleAssessmentQuoteService(
		OpenAiPanelQuoteClient openAiPanelQuoteClient,
		AssessmentQuoteRepository assessmentQuoteRepository,
		S3ImageStorageService s3ImageStorageService,
		Clock clock,
		HttpClient httpClient
	) {
		this.openAiPanelQuoteClient = openAiPanelQuoteClient;
		this.assessmentQuoteRepository = assessmentQuoteRepository;
		this.s3ImageStorageService = s3ImageStorageService;
		this.clock = clock;
		this.httpClient = httpClient;
	}

	@Override
	public boolean shouldGenerateQuote(Assessment assessment) {
		if (assessment == null || assessment.getId() == null || assessment.getId().isBlank()) {
			return false;
		}
		if (assessment.getQuotePdfUrl() != null && !assessment.getQuotePdfUrl().isBlank()) {
			return false;
		}
		if (!AssessmentImageCaptureRules.hasAllRequiredSuccessfulImages(assessment)) {
			return false;
		}
		return assessmentQuoteRepository.findByAssessmentId(assessment.getId()).isEmpty();
	}

	@Override
	public GeneratedQuotePaths generateAndStoreQuote(Assessment assessment) {
		List<OpenAiPanelQuoteClient.PanelRequest> panelRequests = collectReplacementPanels(assessment).entrySet().stream()
			.map(entry -> new OpenAiPanelQuoteClient.PanelRequest(entry.getKey(), entry.getValue()))
			.toList();

		OpenAiPanelQuoteClient.QuoteLookupResult lookupResult = openAiPanelQuoteClient.lookupPanelPrices(
			assessment.getVehicleDetails(),
			panelRequests
		);

		List<QuoteLineItem> lineItems = buildLineItems(assessment.getId(), panelRequests, lookupResult.items());
		lineItems.sort(Comparator.comparing(QuoteLineItem::getPanelLabel));

		double subtotal = lineItems.stream().mapToDouble(QuoteLineItem::getLineTotalZar).sum();
		double vatAmount = roundCurrency(subtotal * VAT_RATE);
		double total = roundCurrency(subtotal + vatAmount);

		AssessmentQuote quote = buildOrUpdateQuote(assessment, lineItems, lookupResult, subtotal, vatAmount, total);
		GeneratedQuotePaths storedPaths = storeQuoteArtifacts(assessment.getId(), assessment, quote);
		quote.setQuoteHtmlUrl(storedPaths.htmlUrl());
		quote.setQuotePdfUrl(storedPaths.pdfUrl());
		assessmentQuoteRepository.save(quote);

		return storedPaths;
	}

	private AssessmentQuote buildOrUpdateQuote(
		Assessment assessment,
		List<QuoteLineItem> lineItems,
		OpenAiPanelQuoteClient.QuoteLookupResult lookupResult,
		double subtotal,
		double vatAmount,
		double total) {
		Instant now = Instant.now(clock);
		AssessmentQuote quote = assessmentQuoteRepository.findByAssessmentId(assessment.getId())
			.orElseGet(() -> new AssessmentQuote(assessment.getId(), assessment.getVehicleDetails(), now, now));
		quote.setVehicleDetails(assessment.getVehicleDetails());
		quote.setLineItems(lineItems);
		quote.setSubtotalZar(roundCurrency(subtotal));
		quote.setVatAmountZar(vatAmount);
		quote.setTotalZar(total);
		quote.setSourceModel(lookupResult.model());
		quote.setTotalTokensUsed(lookupResult.tokensUsed());
		quote.setNotes(buildQuoteNotes(lookupResult.notes(), lineItems.isEmpty()));
		quote.setUpdatedAt(now);
		if (quote.getCreatedAt() == null) {
			quote.setCreatedAt(now);
		}
		return quote;
	}

	private GeneratedQuotePaths storeQuoteArtifacts(String assessmentId, Assessment assessment, AssessmentQuote quote) {
		String template = loadTemplate("vehicle-replacement-quote-template.html");
		String html = populateTemplate(template, assessment, quote);
		StoredImage htmlQuote = s3ImageStorageService.storeAssessmentQuoteHtml(assessmentId, html);
		byte[] pdfBytes = htmlToPdf(html);
		StoredImage pdfQuote = s3ImageStorageService.storeAssessmentQuotePdf(assessmentId, pdfBytes);
		return new GeneratedQuotePaths(htmlQuote.url(), pdfQuote.url());
	}

	@Override
	public byte[] getQuotePdf(String assessmentId) {
		AssessmentQuote quote = assessmentQuoteRepository.findByAssessmentId(assessmentId)
			.orElseThrow(() -> new AssessmentQuoteNotFoundException(assessmentId));
		if (quote.getQuotePdfUrl() == null || quote.getQuotePdfUrl().isBlank()) {
			throw new AssessmentQuoteNotFoundException(assessmentId);
		}
		return s3ImageStorageService.loadFile(quote.getQuotePdfUrl());
	}

	private List<QuoteLineItem> buildLineItems(
		String assessmentId,
		List<OpenAiPanelQuoteClient.PanelRequest> panelRequests,
		List<OpenAiPanelQuoteClient.QuotedPanelProduct> quotedProducts
	) {
		Map<String, OpenAiPanelQuoteClient.QuotedPanelProduct> productByPanel = mapProductsByPanel(quotedProducts);

		List<QuoteLineItem> lineItems = new ArrayList<>();
		for (OpenAiPanelQuoteClient.PanelRequest request : panelRequests) {
			OpenAiPanelQuoteClient.QuotedPanelProduct product = productByPanel.get(request.panelKey());
			if (product == null) {
				lineItems.add(buildNoMatchLineItem(request));
				continue;
			}
			lineItems.add(buildQuotedLineItem(assessmentId, request, product));
		}

		return lineItems;
	}

	private Map<String, OpenAiPanelQuoteClient.QuotedPanelProduct> mapProductsByPanel(
		List<OpenAiPanelQuoteClient.QuotedPanelProduct> quotedProducts) {
		Map<String, OpenAiPanelQuoteClient.QuotedPanelProduct> productByPanel = new HashMap<>();
		if (quotedProducts != null) {
			for (OpenAiPanelQuoteClient.QuotedPanelProduct quotedProduct : quotedProducts) {
				if (quotedProduct == null) {
					continue;
				}
				String key = normalizePanelKey(quotedProduct.panelKey());
				if (key.isBlank() || productByPanel.containsKey(key)) {
					continue;
				}
				productByPanel.put(key, quotedProduct);
			}
		}
		return productByPanel;
	}

	private QuoteLineItem buildNoMatchLineItem(OpenAiPanelQuoteClient.PanelRequest request) {
		return new QuoteLineItem(
			request.panelKey(),
			request.panelLabel(),
			NO_MATCH_PRODUCT_NAME,
			NO_MATCH_PRODUCT_DESCRIPTION,
			"",
			"",
			"",
			"",
			0.0,
			0.0,
			1,
			0.0,
			DEFAULT_HARDWARE_ITEMS,
			NO_MATCH_LINE_ITEM_NOTE);
	}

	private QuoteLineItem buildQuotedLineItem(
		String assessmentId,
		OpenAiPanelQuoteClient.PanelRequest request,
		OpenAiPanelQuoteClient.QuotedPanelProduct product) {
		double panelPrice = roundCurrency(product.panelPriceZar());
		double hardwarePrice = roundCurrency(product.hardwarePriceZar());
		int quantity = product.quantity() <= 0 ? 1 : product.quantity();
		double lineTotal = roundCurrency((panelPrice + hardwarePrice) * quantity);
		String storedProductImageUrl = downloadProductImage(assessmentId, request.panelKey(), product.productImageUrl()).orElse("");

		return new QuoteLineItem(
			request.panelKey(),
			request.panelLabel(),
			fallbackIfBlank(product.productName(), "Product name unavailable"),
			fallbackIfBlank(product.productDescription(), "No description provided"),
			fallbackIfBlank(product.productUrl(), ""),
			fallbackIfBlank(product.productImageUrl(), ""),
			storedProductImageUrl,
			fallbackIfBlank(product.sourceWebsite(), ""),
			panelPrice,
			hardwarePrice,
			quantity,
			lineTotal,
			product.includedHardware() == null || product.includedHardware().isEmpty()
				? DEFAULT_HARDWARE_ITEMS
				: product.includedHardware(),
			fallbackIfBlank(product.notes(), ""));
	}

	private LinkedHashMap<String, String> collectReplacementPanels(Assessment assessment) {
		LinkedHashMap<String, String> panels = new LinkedHashMap<>();
		for (AssessmentImage image : assessment.getImages()) {
			if (image.status() != ImageAnalysisStatus.SUCCESS || image.findings() == null) {
				continue;
			}
			for (VehicleDamageFinding finding : image.findings()) {
				if (!isReplaceAction(finding.recommendedAction())) {
					continue;
				}
				String panelKey = normalizePanelKey(finding.panel());
				if (panelKey.isBlank() || panels.containsKey(panelKey)) {
					continue;
				}
				panels.put(panelKey, VehiclePanelNormalizer.toDisplayLabel(panelKey));
			}
		}
		return panels;
	}

	private Optional<String> downloadProductImage(String assessmentId, String panelKey, String imageUrl) {
		if (imageUrl == null || imageUrl.isBlank()) {
			return Optional.empty();
		}
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(imageUrl))
				.GET()
				.build();
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
				return Optional.empty();
			}
			String contentType = response.headers().firstValue("Content-Type").orElse("image/jpeg");
			StoredImage stored = s3ImageStorageService.storeQuoteProductImage(
				assessmentId,
				panelKey,
				contentType,
				response.body()
			);
			return Optional.of(stored.url());
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	private String buildQuoteNotes(String aiNotes, boolean noPanelsFound) {
		StringBuilder notes = new StringBuilder();
		if (noPanelsFound) {
			notes.append("No replace actions were detected from the assessment findings.");
		}
		if (aiNotes != null && !aiNotes.isBlank()) {
			if (notes.length() > 0) {
				notes.append(' ');
			}
			notes.append(aiNotes.trim());
		}
		if (notes.length() == 0) {
			return DEFAULT_NOTES;
		}
		return notes.toString();
	}

	private String loadTemplate(String fileName) {
		try {
			ClassPathResource classPathResource = new ClassPathResource(fileName);
			byte[] bytes = classPathResource.getInputStream().readAllBytes();
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			throw new OpenAiIntegrationException("Failed to load quote template", exception);
		}
	}

	private String populateTemplate(String template, Assessment assessment, AssessmentQuote quote) {
		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("{{ASSESSMENT_ID}}", escapeHtml(assessment.getId()));
		placeholders.put("{{GENERATED_AT}}", QUOTE_TIMESTAMP_FORMAT.format(Instant.now(clock)));
		placeholders.put("{{QUOTE_ITEM_ROWS}}", buildQuoteRows(quote.getLineItems()));
		placeholders.put("{{VEHICLE_DATA_ROWS}}", buildVehicleRows(assessment));
		placeholders.put("{{QUOTE_SUBTOTAL}}", formatCurrency(quote.getSubtotalZar()));
		placeholders.put("{{QUOTE_VAT}}", formatCurrency(quote.getVatAmountZar()));
		placeholders.put("{{QUOTE_TOTAL}}", formatCurrency(quote.getTotalZar()));
		placeholders.put("{{QUOTE_NOTES}}", escapeHtml(quote.getNotes() == null ? "" : quote.getNotes()));

		String html = template;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			html = html.replace(entry.getKey(), entry.getValue());
		}
		return html;
	}

	private String buildVehicleRows(Assessment assessment) {
		if (assessment.getVehicleDetails() == null) {
			return "<tr><th>Vehicle</th><td>No vehicle details available</td></tr>";
		}

		Map<String, String> rows = new LinkedHashMap<>();
		rows.put("Registration", assessment.getVehicleDetails().registrationNumber());
		rows.put("Make", assessment.getVehicleDetails().make());
		rows.put("Model", assessment.getVehicleDetails().model());
		rows.put("Year", assessment.getVehicleDetails().modelYear());
		rows.put("VIN", assessment.getVehicleDetails().vinNumber());

		StringBuilder builder = new StringBuilder();
		for (Map.Entry<String, String> row : rows.entrySet()) {
			builder.append("<tr><th>")
				.append(escapeHtml(row.getKey()))
				.append("</th><td>")
				.append(escapeHtml(fallbackIfBlank(row.getValue(), "-")))
				.append("</td></tr>");
		}
		return builder.toString();
	}

	private String buildQuoteRows(List<QuoteLineItem> lineItems) {
		if (lineItems == null || lineItems.isEmpty()) {
			return "<tr><td colspan=\"9\">No replacement panels were identified for quoting.</td></tr>";
		}

		StringBuilder builder = new StringBuilder();
		for (QuoteLineItem item : lineItems) {
			builder.append("<tr><td>")
				.append(escapeHtml(fallbackIfBlank(item.getPanelLabel(), "-")))
				.append("</td><td>")
				.append(escapeHtml(fallbackIfBlank(item.getProductName(), "-")))
				.append("</td><td>")
				.append(escapeHtml(fallbackIfBlank(item.getProductDescription(), "-")))
				.append("</td><td>")
				.append(buildProductImageTag(item))
				.append("</td><td>")
				.append(escapeHtml(fallbackIfBlank(item.getProductUrl(), "-")))
				.append("</td><td>")
				.append(formatCurrency(item.getPanelPriceZar()))
				.append("</td><td>")
				.append(formatCurrency(item.getHardwarePriceZar()))
				.append("</td><td>")
				.append(item.getQuantity())
				.append("</td><td>")
				.append(formatCurrency(item.getLineTotalZar()))
				.append("</td></tr>");
		}
		return builder.toString();
	}

	private String buildProductImageTag(QuoteLineItem item) {
		if (item.getStoredProductImageUrl() != null && !item.getStoredProductImageUrl().isBlank()) {
			String dataUri = toDataUri(item.getStoredProductImageUrl());
			if (!dataUri.isBlank()) {
				return "<img class=\"product-image\" src=\"" + dataUri + "\" alt=\"" + escapeHtml(item.getPanelLabel()) + " product\"/>";
			}
		}
		if (item.getProductImageUrl() == null || item.getProductImageUrl().isBlank()) {
			return "-";
		}
		return escapeHtml(item.getProductImageUrl());
	}

	private String toDataUri(String url) {
		try {
			byte[] imageBytes = s3ImageStorageService.loadFile(url);
			String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
			return "data:image/jpeg;base64," + base64;
		} catch (Exception exception) {
			return "";
		}
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
			throw new OpenAiIntegrationException("Failed to render quote PDF", exception);
		}
	}

	private String normalizePanelKey(String panel) {
		return VehiclePanelNormalizer.normalizePanelKey(panel);
	}

	private boolean isReplaceAction(String action) {
		if (action == null) {
			return false;
		}
		String normalized = action.toLowerCase(Locale.ROOT);
		return normalized.contains("replace");
	}

	private double roundCurrency(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private String formatCurrency(double value) {
		return String.format(Locale.ROOT, "R %.2f", roundCurrency(value));
	}

	private String fallbackIfBlank(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private String escapeHtml(String value) {
		return value
			.replace("&", "&amp;")
			.replace("<", "&lt;")
			.replace(">", "&gt;")
			.replace("\"", "&quot;")
			.replace("'", "&#39;");
	}
}
