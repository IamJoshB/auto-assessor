package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.storage.S3ImageStorageService;
import com.nedbank.avo.assessor.storage.StoredImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class VehicleAssessmentReportServiceTest {

	private S3ImageStorageService storageService;
	private VehicleAssessmentReportService reportService;

	@BeforeEach
	void setUp() {
		storageService = mock(S3ImageStorageService.class);
		reportService = new VehicleAssessmentReportService(
			storageService,
			Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneId.of("UTC")));
	}

	@Test
	void shouldGenerateReportReturnsFalseWhenReportAlreadyExists() {
		Assessment assessment = assessmentWithAllRequiredSuccessImages();
		assessment.setReportPdfUrl("/tmp/report.pdf");

		assertThat(reportService.shouldGenerateReport(assessment)).isFalse();
	}

	@Test
	void shouldGenerateReportReturnsTrueWhenAllRequiredAnglesSucceeded() {
		Assessment assessment = assessmentWithAllRequiredSuccessImages();

		assertThat(reportService.shouldGenerateReport(assessment)).isTrue();
	}

	@Test
	void shouldGenerateReportReturnsFalseWhenAnyRequiredImageIsMissing() {
		Assessment assessment = assessmentWithAllRequiredSuccessImages();
		assessment.getImages().removeIf(image -> image.angle() == VehicleImageAngle.ODOMETER);

		assertThat(reportService.shouldGenerateReport(assessment)).isFalse();
	}

	@Test
	void generateAndStoreReportReturnsStoredPaths() {
		Assessment assessment = assessmentWithAllRequiredSuccessImages();
		assessment.setId("a-1");
		given(storageService.storeAssessmentReportHtml(any(String.class), any(String.class)))
			.willReturn(new StoredImage("https://bucket.s3.region.amazonaws.com/a-1/report.html", "text/html"));
		given(storageService.storeAssessmentReportPdf(any(String.class), any(byte[].class)))
			.willReturn(new StoredImage("https://bucket.s3.region.amazonaws.com/a-1/report.pdf", "application/pdf"));

		VehicleAssessmentReportService.GeneratedReportPaths paths = reportService.generateAndStoreReport(assessment);

		assertThat(paths.htmlUrl()).isEqualTo("https://bucket.s3.region.amazonaws.com/a-1/report.html");
		assertThat(paths.pdfUrl()).isEqualTo("https://bucket.s3.region.amazonaws.com/a-1/report.pdf");
	}

	private Assessment assessmentWithAllRequiredSuccessImages() {
		Assessment assessment = new Assessment(vehicleDetails(), Instant.now(), Instant.now());
		assessment.setTotalTokensUsed(10L);
		for (VehicleImageAngle angle : VehicleImageAngle.values()) {
			assessment.getImages().add(new AssessmentImage(
				angle,
				"",
				false,
				10L,
				"img.jpg",
				"image/jpeg",
				"/tmp/original.jpg",
				"/tmp/vehicle.jpg",
				"image/jpeg",
				List.of(),
				Instant.now(),
				ImageAnalysisStatus.SUCCESS,
				null,
				0,
				0L));
		}
		return assessment;
	}

	private VehicleDetails vehicleDetails() {
		return new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of());
	}
}
