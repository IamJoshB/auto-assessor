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
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.AssessmentNotFoundException;
import com.nedbank.avo.assessor.repository.AssessmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class DefaultAdminAssessmentService implements AdminAssessmentService {

	private final AssessmentRepository assessmentRepository;
	private final Clock clock;

	public DefaultAdminAssessmentService(AssessmentRepository assessmentRepository, Clock clock) {
		this.assessmentRepository = assessmentRepository;
		this.clock = clock;
	}

	@Override
	public AssessmentSummaryResponse getAssessmentDetails(String assessmentId) {
		return toSummaryResponse(findAssessment(assessmentId));
	}

	@Override
	public AssessmentSummaryResponse updateAssessment(String assessmentId, UpdateAssessmentRequest request) {
		Assessment assessment = findAssessment(assessmentId);

		assessment.setVehicleDetails(request.vehicleDetails());
		if (request.odometerReading() != null && !request.odometerReading().isBlank()) {
			assessment.setOdometerReading(request.odometerReading());
		}
		assessment.setStatus(AssessmentStatusResolver.resolve(assessment));
		touch(assessment);
		assessmentRepository.save(assessment);
		return toSummaryResponse(assessment);
	}

	@Override
	public void deleteAssessment(String assessmentId) {
		Assessment assessment = findAssessment(assessmentId);
		archive(assessment);
		assessmentRepository.save(assessment);
	}

	@Override
	public void permanentlyDeleteAssessment(String assessmentId) {
		assessmentRepository.deleteById(assessmentId);
	}

	@Override
	public ImageAnalysisDetailsResponse getImageAnalysisDetails(String assessmentId, VehicleImageAngle angle) {
		Assessment assessment = findAssessment(assessmentId);
		return toImageAnalysisDetails(findImageByAngle(assessment, angle));
	}

	@Override
	public ImageAnalysisDetailsResponse retryImageAnalysis(String assessmentId, VehicleImageAngle angle) {
		Assessment assessment = findAssessment(assessmentId);
		AssessmentImage existingImage = findImageByAngle(assessment, angle);

		if (existingImage.status() == ImageAnalysisStatus.SUCCESS) {
			throw new IllegalArgumentException("Cannot retry a successfully analyzed image");
		}

		AssessmentImage retryImage = createRetryPendingImage(existingImage);
		assessment.getImages().remove(existingImage);
		assessment.getImages().add(retryImage);
		touch(assessment);
		assessment.setStatus(AssessmentStatusResolver.resolve(assessment));
		assessmentRepository.save(assessment);

		return toImageAnalysisDetails(retryImage);
	}

	@Override
	public BulkOperationResponse bulkRetryFailedImages(int maxRetries) {
		List<Assessment> assessments = assessmentRepository.findAll();
		int totalProcessed = 0;
		int successCount = 0;
		int failureCount = 0;

		for (Assessment assessment : assessments) {
			if (assessment.isArchived()) {
				continue;
			}

			List<AssessmentImage> failedImages = assessment.getImages().stream()
				.filter(img -> img.status() == ImageAnalysisStatus.FAILED)
				.filter(img -> img.retryCount() < maxRetries)
				.toList();

			for (AssessmentImage failedImage : failedImages) {
				try {
					totalProcessed++;
					AssessmentImage retryImage = createRetryPendingImage(failedImage);

					assessment.getImages().remove(failedImage);
					assessment.getImages().add(retryImage);
					successCount++;
				} catch (Exception exception) {
					failureCount++;
				}
			}

			if (!failedImages.isEmpty()) {
				touch(assessment);
				assessment.setStatus(AssessmentStatusResolver.resolve(assessment));
				assessmentRepository.save(assessment);
			}
		}

		return new BulkOperationResponse(
			totalProcessed,
			successCount,
			failureCount,
			"Bulk retry completed: " + successCount + " marked for retry, " + failureCount + " failed"
		);
	}

	@Override
	public BulkOperationResponse bulkDeleteByStatus(AssessmentStatus status) {
		List<Assessment> assessments = assessmentRepository.findAll();
		int totalProcessed = 0;
		int successCount = 0;
		int failureCount = 0;

		for (Assessment assessment : assessments) {
			if (!assessment.isArchived() && assessment.getStatus() == status) {
				try {
					totalProcessed++;
					archive(assessment);
					assessmentRepository.save(assessment);
					successCount++;
				} catch (Exception exception) {
					failureCount++;
				}
			}
		}

		return new BulkOperationResponse(
			totalProcessed,
			successCount,
			failureCount,
			"Bulk delete completed: " + successCount + " archived, " + failureCount + " failed"
		);
	}

	@Override
	public Page<AssessmentSummaryResponse> getAssessmentsByStatus(AssessmentStatus status, Pageable pageable) {
		return assessmentRepository.findByStatus(status, pageable)
			.map(this::toSummaryResponse);
	}

	@Override
	public Page<AssessmentSummaryResponse> getAllActiveAssessments(Pageable pageable) {
		return assessmentRepository.findAllActive(pageable)
			.map(this::toSummaryResponse);
	}

	@Override
	public AssessmentStatisticsResponse getStatistics() {
		List<Assessment> allAssessments = assessmentRepository.findAll();

		long total = allAssessments.size();
		long completedCount = allAssessments.stream()
			.filter(a -> !a.isArchived() && a.getStatus() == AssessmentStatus.COMPLETED)
			.count();
		long failedCount = allAssessments.stream()
			.filter(a -> !a.isArchived() && a.getStatus() == AssessmentStatus.FAILED)
			.count();
		long partialCount = allAssessments.stream()
			.filter(a -> !a.isArchived() && a.getStatus() == AssessmentStatus.PARTIAL)
			.count();
		long inProgressCount = allAssessments.stream()
			.filter(a -> !a.isArchived() && a.getStatus() == AssessmentStatus.IN_PROGRESS)
			.count();
		long createdCount = allAssessments.stream()
			.filter(a -> !a.isArchived() && a.getStatus() == AssessmentStatus.CREATED)
			.count();
		long archivedCount = allAssessments.stream()
			.filter(Assessment::isArchived)
			.count();

		long totalTokens = allAssessments.stream()
			.filter(a -> !a.isArchived())
			.mapToLong(Assessment::getTotalTokensUsed)
			.sum();
		long activeCount = total - archivedCount;
		long averageTokens = activeCount > 0 ? totalTokens / activeCount : 0;

		return new AssessmentStatisticsResponse(
			total,
			completedCount,
			failedCount,
			partialCount,
			inProgressCount,
			createdCount,
			archivedCount,
			averageTokens
		);
	}

	@Override
	public AssessmentSummaryResponse restoreAssessment(String assessmentId) {
		Assessment assessment = findAssessment(assessmentId);

		if (!assessment.isArchived()) {
			throw new IllegalArgumentException("Assessment is not archived, cannot restore");
		}

		assessment.setArchived(false);
		assessment.setStatus(AssessmentStatusResolver.resolve(assessment));
		touch(assessment);
		assessmentRepository.save(assessment);

		return toSummaryResponse(assessment);
	}

	private Assessment findAssessment(String assessmentId) {
		return assessmentRepository.findById(assessmentId)
			.orElseThrow(() -> new AssessmentNotFoundException(assessmentId));
	}

	private AssessmentImage findImageByAngle(Assessment assessment, VehicleImageAngle angle) {
		return assessment.getImages().stream()
			.filter(image -> image.angle() == angle)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Image not found for angle: " + angle));
	}

	private AssessmentImage createRetryPendingImage(AssessmentImage existingImage) {
		return new AssessmentImage(
			existingImage.angle(),
			existingImage.odometerReading(),
			existingImage.redactionApplied(),
			existingImage.tokensUsed(),
			existingImage.originalFilename(),
			existingImage.originalContentType(),
			existingImage.originalImageUrl(),
			existingImage.vehicleImageUrl(),
			existingImage.vehicleImageContentType(),
			existingImage.findings(),
			Instant.now(clock),
			ImageAnalysisStatus.RETRY_PENDING,
			null,
			existingImage.retryCount() + 1,
			0L
		);
	}

	private ImageAnalysisDetailsResponse toImageAnalysisDetails(AssessmentImage image) {
		return new ImageAnalysisDetailsResponse(
			image.angle(),
			image.status(),
			image.vehicleImageUrl(),
			image.vehicleImageContentType(),
			image.tokensUsed(),
			image.retryCount(),
			image.errorMessage(),
			image.uploadedAt()
		);
	}

	private void archive(Assessment assessment) {
		assessment.setArchived(true);
		assessment.setStatus(AssessmentStatus.ARCHIVED);
		touch(assessment);
	}

	private void touch(Assessment assessment) {
		assessment.setUpdatedAt(Instant.now(clock));
	}

	private AssessmentSummaryResponse toSummaryResponse(Assessment assessment) {
		long successfulImages = assessment.getImages().stream()
			.filter(img -> img.status() == ImageAnalysisStatus.SUCCESS)
			.count();
		long failedImages = assessment.getImages().stream()
			.filter(img -> img.status() == ImageAnalysisStatus.FAILED)
			.count();

		return new AssessmentSummaryResponse(
			assessment.getId(),
			assessment.getStatus(),
			assessment.getVehicleDetails(),
			assessment.getImages().size(),
			(int) successfulImages,
			(int) failedImages,
			assessment.getTotalTokensUsed(),
			assessment.getErrorMessage(),
			assessment.getCreatedAt(),
			assessment.getUpdatedAt()
		);
	}

}
