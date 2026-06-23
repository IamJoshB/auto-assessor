package com.nedbank.avo.assessor.api.dto.admin;

import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.VehicleDetails;

import java.time.Instant;

public record AssessmentSummaryResponse(
	String assessmentId,
	AssessmentStatus status,
	VehicleDetails vehicleDetails,
	int totalImages,
	int successfulImages,
	int failedImages,
	long totalTokensUsed,
	String errorMessage,
	Instant createdAt,
	Instant updatedAt
) {
}
