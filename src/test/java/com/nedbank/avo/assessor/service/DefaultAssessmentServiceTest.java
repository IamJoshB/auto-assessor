package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.api.dto.AssessmentImageUploadResponse;
import com.nedbank.avo.assessor.api.dto.CreateAssessmentResponse;
import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.AssessmentReportNotFoundException;
import com.nedbank.avo.assessor.exception.DuplicateVehicleAngleException;
import com.nedbank.avo.assessor.repository.AssessmentRepository;
import com.nedbank.avo.assessor.storage.S3ImageStorageService;
import com.nedbank.avo.assessor.storage.StoredImage;
import com.nedbank.avo.assessor.vision.OpenAiVisionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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

class DefaultAssessmentServiceTest {

	private AssessmentRepository assessmentRepository;
	private OpenAiVisionClient openAiVisionClient;
	private S3ImageStorageService s3ImageStorageService;
	private VehicleAssessmentReportService vehicleAssessmentReportService;
	private VehicleAssessmentQuoteService vehicleAssessmentQuoteService;
	private DefaultAssessmentService service;
	private Clock clock;

	@BeforeEach
	void setUp() {
		assessmentRepository = mock(AssessmentRepository.class);
		openAiVisionClient = mock(OpenAiVisionClient.class);
		s3ImageStorageService = mock(S3ImageStorageService.class);
		vehicleAssessmentReportService = mock(VehicleAssessmentReportService.class);
		vehicleAssessmentQuoteService = mock(VehicleAssessmentQuoteService.class);
		clock = Clock.fixed(Instant.parse("2026-06-22T12:00:00Z"), ZoneId.of("UTC"));
		service = new DefaultAssessmentService(
			assessmentRepository,
			openAiVisionClient,
			s3ImageStorageService,
			vehicleAssessmentReportService,
			vehicleAssessmentQuoteService,
			clock);
	}

	@Test
	void createAssessmentStoresLicenseDiscAndReturnsResponse() {
		VehicleDetails details = vehicleDetails();
		MockMultipartFile licenseDisc = new MockMultipartFile("licenseDiscImage", "disc.jpg", "image/jpeg", "image".getBytes());
		Assessment created = new Assessment(details, Instant.now(clock), Instant.now(clock));
		created.setId("assessment-1");
		given(openAiVisionClient.extractVehicleDetails("image/jpeg", "image".getBytes()))
			.willReturn(new OpenAiVisionClient.VehicleDetailsResult(details, 120));
		given(assessmentRepository.save(any(Assessment.class))).willAnswer(invocation -> {
			Assessment arg = invocation.getArgument(0);
			if (arg.getId() == null) {
				arg.setId("assessment-1");
			}
			return arg;
		});
		given(s3ImageStorageService.storeLicenseDisc("assessment-1", "image/jpeg", "image".getBytes()))
			.willReturn(new StoredImage("https://bucket.s3.region.amazonaws.com/assessment-1/license.jpg", "image/jpeg"));

		CreateAssessmentResponse response = service.createAssessment(licenseDisc);

		assertThat(response.assessmentId()).isEqualTo("assessment-1");
		assertThat(response.licenseDiscImageUrl()).isEqualTo("https://bucket.s3.region.amazonaws.com/assessment-1/license.jpg");
		assertThat(response.totalTokensUsed()).isEqualTo(120L);
	}

	@Test
	void analyzeVehicleImageThrowsWhenAngleAlreadyExists() {
		Assessment assessment = new Assessment(vehicleDetails(), Instant.now(clock), Instant.now(clock));
		assessment.setId("assessment-1");
		assessment.getImages().add(existingImage(VehicleImageAngle.FRONT_VIEW));
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));

		MultipartFile image = new MockMultipartFile("image", "front.jpg", "image/jpeg", "image".getBytes());

		assertThatThrownBy(() -> service.analyzeVehicleImage("assessment-1", VehicleImageAngle.FRONT_VIEW, image))
			.isInstanceOf(DuplicateVehicleAngleException.class);
	}

	@Test
	void analyzeVehicleImageSetsAssessmentStatusAndReturnsUploadResponse() {
		Assessment assessment = new Assessment(vehicleDetails(), Instant.now(clock), Instant.now(clock));
		assessment.setId("assessment-1");
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));
		given(assessmentRepository.save(any(Assessment.class))).willAnswer(invocation -> invocation.getArgument(0));
		given(vehicleAssessmentReportService.shouldGenerateReport(any(Assessment.class))).willReturn(false);
		given(vehicleAssessmentQuoteService.shouldGenerateQuote(any(Assessment.class))).willReturn(false);

		MockMultipartFile image = new MockMultipartFile("image", "front.jpg", "image/jpeg", "front".getBytes());
		given(openAiVisionClient.analyzeVehicleDamage(
			VehicleImageAngle.FRONT_VIEW,
			assessment.getVehicleDetails(),
			"image/jpeg",
			"front".getBytes()))
			.willReturn(new OpenAiVisionClient.DamageAnalysisResult(
				"",
				80,
				false,
				"image/jpeg",
				"annotated".getBytes(),
				List.of(new OpenAiVisionClient.DamageFindingResult("scratch", "minor", "small scratch", "front bumper", "fix"))));
		given(s3ImageStorageService.storeOriginal("assessment-1", VehicleImageAngle.FRONT_VIEW, "image/jpeg", "front".getBytes()))
			.willReturn(new StoredImage("https://bucket.s3.region.amazonaws.com/assessment-1/original.jpg", "image/jpeg"));
		given(s3ImageStorageService.storeVehicleImage("assessment-1", VehicleImageAngle.FRONT_VIEW, "image/jpeg", "annotated".getBytes()))
			.willReturn(new StoredImage("https://bucket.s3.region.amazonaws.com/assessment-1/vehicle.jpg", "image/jpeg"));

		AssessmentImageUploadResponse response = service.analyzeVehicleImage("assessment-1", VehicleImageAngle.FRONT_VIEW, image);

		assertThat(response.assessmentId()).isEqualTo("assessment-1");
		assertThat(response.totalTokensUsed()).isEqualTo(80L);
		assertThat(assessment.getStatus()).isEqualTo(AssessmentStatus.COMPLETED);
		assertThat(assessment.getImages()).hasSize(1);
	}

	@Test
	void getAssessmentReportPdfThrowsWhenReportPathMissing() {
		Assessment assessment = new Assessment(vehicleDetails(), Instant.now(clock), Instant.now(clock));
		assessment.setId("assessment-1");
		assessment.setReportPdfUrl("  ");
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));

		assertThatThrownBy(() -> service.getAssessmentReportPdf("assessment-1"))
			.isInstanceOf(AssessmentReportNotFoundException.class);
	}

	private VehicleDetails vehicleDetails() {
		return new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of());
	}

	private AssessmentImage existingImage(VehicleImageAngle angle) {
		return new AssessmentImage(
			angle,
			"",
			false,
			10L,
			"f.jpg",
			"image/jpeg",
			"/tmp/original.jpg",
			"/tmp/vehicle.jpg",
			"image/jpeg",
			List.of(new VehicleDamageFinding("scratch", "minor", "x", "front", "fix")),
			Instant.now(clock),
			ImageAnalysisStatus.SUCCESS,
			null,
			0,
			0L);
	}
}
