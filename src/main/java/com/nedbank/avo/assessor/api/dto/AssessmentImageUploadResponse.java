package com.nedbank.avo.assessor.api.dto;

import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;

import java.time.Instant;
import java.util.List;

public record AssessmentImageUploadResponse(
	String assessmentId,
	VehicleImageAngle angle,
	String odometerReading,
	long totalTokensUsed,
	boolean redactionApplied,
	List<VehicleDamageFinding> findings,
	String originalImageUrl,
	String vehicleImageUrl,
	String vehicleImageContentType,
	Instant uploadedAt
) {
}