package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;

final class AssessmentStatusResolver {

	private AssessmentStatusResolver() {
	}

	static AssessmentStatus resolve(Assessment assessment) {
		if (assessment.isArchived()) {
			return AssessmentStatus.ARCHIVED;
		}

		if (assessment.getImages().isEmpty()) {
			return AssessmentStatus.CREATED;
		}

		long successCount = countImagesByStatus(assessment, ImageAnalysisStatus.SUCCESS);
		long failedCount = countImagesByStatus(assessment, ImageAnalysisStatus.FAILED);
		long inProgressLikeCount = countImagesByStatus(assessment, ImageAnalysisStatus.PENDING)
			+ countImagesByStatus(assessment, ImageAnalysisStatus.IN_PROGRESS)
			+ countImagesByStatus(assessment, ImageAnalysisStatus.RETRY_PENDING);

		if (failedCount > 0 && successCount == 0 && inProgressLikeCount == 0) {
			return AssessmentStatus.FAILED;
		}
		if (inProgressLikeCount > 0) {
			return AssessmentStatus.IN_PROGRESS;
		}
		if (successCount > 0 && failedCount == 0) {
			return AssessmentStatus.COMPLETED;
		}
		if (successCount > 0 && failedCount > 0) {
			return AssessmentStatus.PARTIAL;
		}
		return AssessmentStatus.IN_PROGRESS;
	}

	private static long countImagesByStatus(Assessment assessment, ImageAnalysisStatus status) {
		return assessment.getImages().stream()
			.filter(image -> image.status() == status)
			.count();
	}
}
