package com.nedbank.avo.assessor.api.dto.admin;

import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;

import java.time.Instant;

public record ImageAnalysisDetailsResponse(
	VehicleImageAngle angle,
	ImageAnalysisStatus status,
	String vehicleImageUrl,
	String vehicleImageContentType,
	long tokensUsed,
	int retryCount,
	String errorMessage,
	Instant uploadedAt
) {
}
