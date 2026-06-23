package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentStatusResolverTest {

	@Test
	void resolvesArchivedWhenAssessmentIsArchived() {
		Assessment assessment = createAssessment();
		assessment.setArchived(true);
		assessment.getImages().add(createImage(ImageAnalysisStatus.SUCCESS));

		assertThat(AssessmentStatusResolver.resolve(assessment)).isEqualTo(AssessmentStatus.ARCHIVED);
	}

	@Test
	void resolvesCreatedWhenNoImagesExist() {
		Assessment assessment = createAssessment();

		assertThat(AssessmentStatusResolver.resolve(assessment)).isEqualTo(AssessmentStatus.CREATED);
	}

	@Test
	void resolvesCompletedWhenAllImagesAreSuccessful() {
		Assessment assessment = createAssessment();
		assessment.getImages().add(createImage(ImageAnalysisStatus.SUCCESS));
		assessment.getImages().add(createImage(ImageAnalysisStatus.SUCCESS));

		assertThat(AssessmentStatusResolver.resolve(assessment)).isEqualTo(AssessmentStatus.COMPLETED);
	}

	@Test
	void resolvesFailedWhenAllImagesFailedAndNoneInProgress() {
		Assessment assessment = createAssessment();
		assessment.getImages().add(createImage(ImageAnalysisStatus.FAILED));
		assessment.getImages().add(createImage(ImageAnalysisStatus.FAILED));

		assertThat(AssessmentStatusResolver.resolve(assessment)).isEqualTo(AssessmentStatus.FAILED);
	}

	@Test
	void resolvesPartialWhenSuccessAndFailedImagesCoexist() {
		Assessment assessment = createAssessment();
		assessment.getImages().add(createImage(ImageAnalysisStatus.SUCCESS));
		assessment.getImages().add(createImage(ImageAnalysisStatus.FAILED));

		assertThat(AssessmentStatusResolver.resolve(assessment)).isEqualTo(AssessmentStatus.PARTIAL);
	}

	@Test
	void resolvesInProgressWhenPendingOrRetryPendingImagesExist() {
		Assessment pendingAssessment = createAssessment();
		pendingAssessment.getImages().add(createImage(ImageAnalysisStatus.PENDING));

		Assessment retryPendingAssessment = createAssessment();
		retryPendingAssessment.getImages().add(createImage(ImageAnalysisStatus.RETRY_PENDING));

		assertThat(AssessmentStatusResolver.resolve(pendingAssessment)).isEqualTo(AssessmentStatus.IN_PROGRESS);
		assertThat(AssessmentStatusResolver.resolve(retryPendingAssessment)).isEqualTo(AssessmentStatus.IN_PROGRESS);
	}

	private Assessment createAssessment() {
		VehicleDetails details = new VehicleDetails(
			"CA123456",
			"Toyota",
			"Corolla",
			"2022",
			"White",
			"ENG123",
			"VIN123",
			"Passenger",
			"2026-09-30",
			Map.of());
		Instant now = Instant.parse("2026-06-22T10:00:00Z");
		Assessment assessment = new Assessment(details, now, now);
		assessment.setStatus(AssessmentStatus.CREATED);
		return assessment;
	}

	private AssessmentImage createImage(ImageAnalysisStatus status) {
		return new AssessmentImage(
			VehicleImageAngle.FRONT_VIEW,
			"120000",
			false,
			100L,
			"front.jpg",
			"image/jpeg",
			"./storage/original/front.jpg",
			"./storage/vehicle/front.jpg",
			"image/jpeg",
			List.of(),
			Instant.parse("2026-06-22T10:00:00Z"),
			status,
			null,
			0,
			0L);
	}
}
