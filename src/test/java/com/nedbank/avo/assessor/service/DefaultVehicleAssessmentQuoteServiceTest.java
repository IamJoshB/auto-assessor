package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.AssessmentQuote;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.AssessmentQuoteNotFoundException;
import com.nedbank.avo.assessor.quote.OpenAiPanelQuoteClient;
import com.nedbank.avo.assessor.repository.AssessmentQuoteRepository;
import com.nedbank.avo.assessor.storage.S3ImageStorageService;
import com.nedbank.avo.assessor.storage.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultVehicleAssessmentQuoteServiceTest {

	private OpenAiPanelQuoteClient quoteClient;
	private AssessmentQuoteRepository quoteRepository;
	private S3ImageStorageService storageService;
	private DefaultVehicleAssessmentQuoteService service;

	@BeforeEach
	void setUp() {
		quoteClient = mock(OpenAiPanelQuoteClient.class);
		quoteRepository = mock(AssessmentQuoteRepository.class);
		storageService = mock(S3ImageStorageService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneId.of("UTC"));
		service = new DefaultVehicleAssessmentQuoteService(
			quoteClient,
			quoteRepository,
			storageService,
			clock,
			HttpClient.newHttpClient());
	}

	@Test
	void shouldGenerateQuoteReturnsFalseForInvalidAssessmentOrExistingQuote() {
		Assessment nullAssessment = null;
		Assessment withoutId = new Assessment(vehicleDetails(), Instant.now(), Instant.now());
		Assessment withQuotePath = new Assessment(vehicleDetails(), Instant.now(), Instant.now());
		withQuotePath.setId("a-1");
		withQuotePath.setQuotePdfUrl("/tmp/quote.pdf");

		assertThat(service.shouldGenerateQuote(nullAssessment)).isFalse();
		assertThat(service.shouldGenerateQuote(withoutId)).isFalse();
		assertThat(service.shouldGenerateQuote(withQuotePath)).isFalse();
	}

	@Test
	void shouldGenerateQuoteReturnsTrueWhenNoQuoteExists() {
		Assessment assessment = new Assessment(vehicleDetails(), Instant.now(), Instant.now());
		assessment.setId("a-1");
		addAllRequiredSuccessImages(assessment);
		given(quoteRepository.findByAssessmentId("a-1")).willReturn(Optional.empty());

		assertThat(service.shouldGenerateQuote(assessment)).isTrue();
	}

	@Test
	void shouldGenerateQuoteReturnsFalseWhenNotAllVehicleImagesAreCaptured() {
		Assessment assessment = new Assessment(vehicleDetails(), Instant.now(), Instant.now());
		assessment.setId("a-1");
		assessment.getImages().add(successImage(VehicleImageAngle.FRONT_VIEW));

		assertThat(service.shouldGenerateQuote(assessment)).isFalse();
	}

	@Test
	void getQuotePdfThrowsWhenMissingOrPathBlank() {
		AssessmentQuote quote = new AssessmentQuote("a-1", vehicleDetails(), Instant.now(), Instant.now());
		quote.setQuotePdfUrl(" ");
		given(quoteRepository.findByAssessmentId("a-1")).willReturn(Optional.of(quote));

		assertThatThrownBy(() -> service.getQuotePdf("a-1"))
			.isInstanceOf(AssessmentQuoteNotFoundException.class);
	}

	@Test
	void getQuotePdfLoadsStoredPdfWhenPathExists() {
		AssessmentQuote quote = new AssessmentQuote("a-1", vehicleDetails(), Instant.now(), Instant.now());
		quote.setQuotePdfUrl("/tmp/quote.pdf");
		given(quoteRepository.findByAssessmentId("a-1")).willReturn(Optional.of(quote));
		given(storageService.loadFile("/tmp/quote.pdf")).willReturn("pdf".getBytes());

		byte[] pdf = service.getQuotePdf("a-1");

		assertThat(pdf).isEqualTo("pdf".getBytes());
	}

	@Test
	void generateAndStoreQuotePersistsArtifactsWhenNoPanelsFound() {
		Assessment assessment = new Assessment(vehicleDetails(), Instant.now(), Instant.now());
		assessment.setId("a-1");
		given(quoteClient.lookupPanelPrices(any(VehicleDetails.class), org.mockito.ArgumentMatchers.<List<OpenAiPanelQuoteClient.PanelRequest>>any()))
			.willReturn(new OpenAiPanelQuoteClient.QuoteLookupResult(List.of(), 0, "test-model", "none"));
		given(quoteRepository.findByAssessmentId("a-1")).willReturn(Optional.empty());
		given(storageService.storeAssessmentQuoteHtml(any(String.class), any(String.class)))
			.willReturn(new StoredImage("https://bucket.s3.region.amazonaws.com/a-1/quote.html", "text/html"));
		given(storageService.storeAssessmentQuotePdf(any(String.class), any(byte[].class)))
			.willReturn(new StoredImage("https://bucket.s3.region.amazonaws.com/a-1/quote.pdf", "application/pdf"));
		given(quoteRepository.save(any(AssessmentQuote.class))).willAnswer(invocation -> invocation.getArgument(0));

		VehicleAssessmentQuoteService.GeneratedQuotePaths paths = service.generateAndStoreQuote(assessment);

		assertThat(paths.htmlUrl()).isEqualTo("https://bucket.s3.region.amazonaws.com/a-1/quote.html");
		assertThat(paths.pdfUrl()).isEqualTo("https://bucket.s3.region.amazonaws.com/a-1/quote.pdf");
		verify(quoteRepository).save(any(AssessmentQuote.class));
	}

	private VehicleDetails vehicleDetails() {
		return new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of());
	}

	private void addAllRequiredSuccessImages(Assessment assessment) {
		for (VehicleImageAngle angle : VehicleImageAngle.values()) {
			assessment.getImages().add(successImage(angle));
		}
	}

	private AssessmentImage successImage(VehicleImageAngle angle) {
		return new AssessmentImage(
			angle,
			"",
			false,
			10L,
			"f.jpg",
			"image/jpeg",
			"https://bucket.s3.region.amazonaws.com/a-1/original.jpg",
			"https://bucket.s3.region.amazonaws.com/a-1/vehicle.jpg",
			"image/jpeg",
			List.of(new VehicleDamageFinding("scratch", "minor", "x", "front", "fix")),
			Instant.now(),
			ImageAnalysisStatus.SUCCESS,
			null,
			0,
			0L);
	}
}
