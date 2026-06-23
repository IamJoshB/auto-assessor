package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.api.dto.admin.AssessmentStatisticsResponse;
import com.nedbank.avo.assessor.api.dto.admin.AssessmentSummaryResponse;
import com.nedbank.avo.assessor.api.dto.admin.BulkOperationResponse;
import com.nedbank.avo.assessor.api.dto.admin.ImageAnalysisDetailsResponse;
import com.nedbank.avo.assessor.api.dto.admin.UpdateAssessmentRequest;
import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.exception.AssessmentNotFoundException;
import com.nedbank.avo.assessor.repository.AssessmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DefaultAdminAssessmentServiceTest {

	private AssessmentRepository assessmentRepository;
	private AdminAssessmentService adminAssessmentService;
	private Clock clock;
	private Instant fixedTime;

	@BeforeEach
	void setUp() {
		assessmentRepository = mock(AssessmentRepository.class);
		fixedTime = Instant.parse("2026-06-11T10:00:00Z");
		clock = Clock.fixed(fixedTime, ZoneId.systemDefault());
		adminAssessmentService = new DefaultAdminAssessmentService(assessmentRepository, clock);
	}

	@Test
	void getAssessmentDetailsReturnsAssessmentSummary() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));

		AssessmentSummaryResponse result = adminAssessmentService.getAssessmentDetails("assessment-1");

		assertThat(result).isNotNull();
		assertThat(result.assessmentId()).isEqualTo("assessment-1");
		assertThat(result.status()).isEqualTo(AssessmentStatus.COMPLETED);
		assertThat(result.totalImages()).isEqualTo(3);
		assertThat(result.successfulImages()).isEqualTo(3);
		assertThat(result.failedImages()).isEqualTo(0);
	}

	@Test
	void getAssessmentDetailsThrowsWhenNotFound() {
		given(assessmentRepository.findById("non-existent")).willReturn(Optional.empty());

		assertThatThrownBy(() -> adminAssessmentService.getAssessmentDetails("non-existent"))
			.isInstanceOf(AssessmentNotFoundException.class);
	}

	@Test
	void updateAssessmentUpdatesVehicleDetailsAndTimestamp() {
		var originalDetails = new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of());
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		assessment.setVehicleDetails(originalDetails);

		var newDetails = new VehicleDetails("CA654321", "Honda", "Civic", "2023", "Blue", "ENG456", "VIN456", "Passenger", "2027-09-30", Map.of());
		var updateRequest = new UpdateAssessmentRequest(newDetails, "100000", "Updated");

		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));
		given(assessmentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		AssessmentSummaryResponse result = adminAssessmentService.updateAssessment("assessment-1", updateRequest);

		assertThat(result.vehicleDetails().make()).isEqualTo("Honda");
		assertThat(result.vehicleDetails().model()).isEqualTo("Civic");
		verify(assessmentRepository).save(any(Assessment.class));
	}

	@Test
	void updateAssessmentRecomputesStatusWhenPersistedStatusIsStale() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.CREATED, 2, 2);
		var newDetails = new VehicleDetails("CA654321", "Honda", "Civic", "2023", "Blue", "ENG456", "VIN456", "Passenger", "2027-09-30", Map.of());
		var updateRequest = new UpdateAssessmentRequest(newDetails, "100000", "Updated");

		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));
		given(assessmentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		AssessmentSummaryResponse result = adminAssessmentService.updateAssessment("assessment-1", updateRequest);

		assertThat(result.status()).isEqualTo(AssessmentStatus.COMPLETED);
		assertThat(assessment.getStatus()).isEqualTo(AssessmentStatus.COMPLETED);
		verify(assessmentRepository).save(any(Assessment.class));
	}

	@Test
	void deleteAssessmentArchivesAssessment() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));
		given(assessmentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		adminAssessmentService.deleteAssessment("assessment-1");

		assertThat(assessment.isArchived()).isTrue();
		assertThat(assessment.getStatus()).isEqualTo(AssessmentStatus.ARCHIVED);
		verify(assessmentRepository).save(assessment);
	}

	@Test
	void permanentlyDeleteAssessmentDeletesFromRepository() {
		adminAssessmentService.permanentlyDeleteAssessment("assessment-1");

		verify(assessmentRepository).deleteById("assessment-1");
	}

	@Test
	void getImageAnalysisDetailsReturnsImageDetails() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));

		ImageAnalysisDetailsResponse result = adminAssessmentService.getImageAnalysisDetails("assessment-1", VehicleImageAngle.FRONT_VIEW);

		assertThat(result).isNotNull();
		assertThat(result.angle()).isEqualTo(VehicleImageAngle.FRONT_VIEW);
		assertThat(result.status()).isEqualTo(ImageAnalysisStatus.SUCCESS);
		assertThat(result.tokensUsed()).isEqualTo(300);
		assertThat(result.retryCount()).isEqualTo(0);
	}

	@Test
	void getImageAnalysisDetailsThrowsWhenImageNotFound() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 1, 1);
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));

		assertThatThrownBy(() -> adminAssessmentService.getImageAnalysisDetails("assessment-1", VehicleImageAngle.REAR_VIEW))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Image not found");
	}

	@Test
	void retryImageAnalysisMarksImageForRetry() {
		var assessment = createTestAssessmentWithFailedImage("assessment-1", VehicleImageAngle.FRONT_VIEW);
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));
		given(assessmentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		ImageAnalysisDetailsResponse result = adminAssessmentService.retryImageAnalysis("assessment-1", VehicleImageAngle.FRONT_VIEW);

		assertThat(result.status()).isEqualTo(ImageAnalysisStatus.RETRY_PENDING);
		assertThat(result.retryCount()).isEqualTo(1);
		verify(assessmentRepository).save(any(Assessment.class));
	}

	@Test
	void retryImageAnalysisThrowsWhenImageIsSuccessful() {
		var assessment = createTestAssessmentWithSingleImage("assessment-1", VehicleImageAngle.FRONT_VIEW, ImageAnalysisStatus.SUCCESS);
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));

		assertThatThrownBy(() -> adminAssessmentService.retryImageAnalysis("assessment-1", VehicleImageAngle.FRONT_VIEW))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Cannot retry a successfully analyzed image");
	}

	@Test
	void bulkRetryFailedImagesMarksMultipleImagesForRetry() {
		var assessment1 = createTestAssessmentWithFailedImage("assessment-1", VehicleImageAngle.FRONT_VIEW);
		var assessment2 = createTestAssessmentWithFailedImage("assessment-2", VehicleImageAngle.REAR_VIEW);

		given(assessmentRepository.findAll()).willReturn(List.of(assessment1, assessment2));
		given(assessmentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		BulkOperationResponse result = adminAssessmentService.bulkRetryFailedImages(3);

		assertThat(result.totalProcessed()).isEqualTo(2);
		assertThat(result.successCount()).isEqualTo(2);
		assertThat(result.failureCount()).isEqualTo(0);
		verify(assessmentRepository, times(2)).save(any());
	}

	@Test
	void bulkDeleteByStatusArchivesMultipleAssessments() {
		var assessment1 = createTestAssessment("assessment-1", AssessmentStatus.FAILED, 2, 1);
		var assessment2 = createTestAssessment("assessment-2", AssessmentStatus.FAILED, 1, 0);
		var assessment3 = createTestAssessment("assessment-3", AssessmentStatus.COMPLETED, 3, 3);

		given(assessmentRepository.findAll()).willReturn(List.of(assessment1, assessment2, assessment3));
		given(assessmentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		BulkOperationResponse result = adminAssessmentService.bulkDeleteByStatus(AssessmentStatus.FAILED);

		assertThat(result.totalProcessed()).isEqualTo(2);
		assertThat(result.successCount()).isEqualTo(2);
		assertThat(result.failureCount()).isEqualTo(0);
		verify(assessmentRepository, times(2)).save(any());
	}

	@Test
	void getAssessmentsByStatusReturnsPaginatedResults() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		var page = new PageImpl<>(List.of(assessment), PageRequest.of(0, 20), 1);
		given(assessmentRepository.findByStatus(AssessmentStatus.COMPLETED, PageRequest.of(0, 20))).willReturn(page);

		Page<AssessmentSummaryResponse> result = adminAssessmentService.getAssessmentsByStatus(AssessmentStatus.COMPLETED, PageRequest.of(0, 20));

		assertThat(result).isNotEmpty();
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).assessmentId()).isEqualTo("assessment-1");
	}

	@Test
	void getStatisticsReturnsCorrectMetrics() {
		var completed = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		var failed = createTestAssessment("assessment-2", AssessmentStatus.FAILED, 2, 1);
		var partial = createTestAssessment("assessment-3", AssessmentStatus.PARTIAL, 3, 2);
		var archived = createTestAssessment("assessment-4", AssessmentStatus.ARCHIVED, 2, 1);
		archived.setArchived(true);

		completed.setTotalTokensUsed(1000);
		failed.setTotalTokensUsed(500);
		partial.setTotalTokensUsed(750);

		given(assessmentRepository.findAll()).willReturn(List.of(completed, failed, partial, archived));

		AssessmentStatisticsResponse result = adminAssessmentService.getStatistics();

		assertThat(result.totalAssessments()).isEqualTo(4);
		assertThat(result.completedCount()).isEqualTo(1);
		assertThat(result.failedCount()).isEqualTo(1);
		assertThat(result.partialCount()).isEqualTo(1);
		assertThat(result.archivedCount()).isEqualTo(1);
		assertThat(result.averageTokensPerAssessment()).isEqualTo(750); // (1000 + 500 + 750) / 3
	}

	@Test
	void restoreAssessmentUnarchivesAssessment() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		assessment.setArchived(true);
		assessment.setStatus(AssessmentStatus.ARCHIVED);

		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));
		given(assessmentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

		AssessmentSummaryResponse result = adminAssessmentService.restoreAssessment("assessment-1");

		assertThat(assessment.isArchived()).isFalse();
		assertThat(result.status()).isEqualTo(AssessmentStatus.COMPLETED);
		verify(assessmentRepository).save(assessment);
	}

	@Test
	void restoreAssessmentThrowsWhenAssessmentNotArchived() {
		var assessment = createTestAssessment("assessment-1", AssessmentStatus.COMPLETED, 3, 3);
		given(assessmentRepository.findById("assessment-1")).willReturn(Optional.of(assessment));

		assertThatThrownBy(() -> adminAssessmentService.restoreAssessment("assessment-1"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("not archived");
	}

	// Helper methods

	private Assessment createTestAssessment(String id, AssessmentStatus status, int totalImages, int successfulImages) {
		var vehicleDetails = new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of());
		var assessment = new Assessment(vehicleDetails, fixedTime, fixedTime);
		assessment.setId(id);
		assessment.setStatus(status);
		assessment.setTotalTokensUsed(1000);

		// Add images
		for (int i = 0; i < totalImages; i++) {
			var angle = VehicleImageAngle.values()[i % VehicleImageAngle.values().length];
			var imageStatus = i < successfulImages ? ImageAnalysisStatus.SUCCESS : ImageAnalysisStatus.FAILED;
			var image = new AssessmentImage(
				angle,
				"120000",
				true,
				300,
				"image.jpg",
				"image/jpeg",
				"./storage/original.jpg",
				"./storage/damage.jpg",
				"image/jpeg",
				List.of(new VehicleDamageFinding("scratch", "minor", "Minor scratch", "Front bumper", "fix")),
				fixedTime,
				imageStatus,
				imageStatus == ImageAnalysisStatus.FAILED ? "Analysis failed" : null,
				0,
				0L
			);
			assessment.getImages().add(image);
		}

		return assessment;
	}

	private Assessment createTestAssessmentWithFailedImage(String id, VehicleImageAngle angle) {
		var vehicleDetails = new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of());
		var assessment = new Assessment(vehicleDetails, fixedTime, fixedTime);
		assessment.setId(id);
		assessment.setStatus(AssessmentStatus.FAILED);
		assessment.setTotalTokensUsed(500);

		var failedImage = new AssessmentImage(
			angle,
			"120000",
			false,
			250L,
			"image.jpg",
			"image/jpeg",
			"./storage/original.jpg",
			"./storage/damage.jpg",
			"image/jpeg",
			List.of(),
			fixedTime,
			ImageAnalysisStatus.FAILED,
			"OpenAI API timeout",
			0,
			0L
		);
		assessment.getImages().add(failedImage);

		return assessment;
	}

	private Assessment createTestAssessmentWithSingleImage(String id, VehicleImageAngle angle, ImageAnalysisStatus status) {
		var vehicleDetails = new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of());
		var assessment = new Assessment(vehicleDetails, fixedTime, fixedTime);
		assessment.setId(id);
		assessment.setStatus(AssessmentStatusResolver.resolve(assessment));
		assessment.setTotalTokensUsed(500);

		assessment.getImages().add(new AssessmentImage(
			angle,
			"120000",
			false,
			250L,
			"image.jpg",
			"image/jpeg",
			"./storage/original.jpg",
			"./storage/damage.jpg",
			"image/jpeg",
			List.of(),
			fixedTime,
			status,
			status == ImageAnalysisStatus.FAILED ? "OpenAI API timeout" : null,
			0,
			0L
		));

		assessment.setStatus(AssessmentStatusResolver.resolve(assessment));
		return assessment;
	}
}
