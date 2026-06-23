package com.nedbank.avo.assessor.domain;

import java.time.Instant;
import java.util.List;

public record AssessmentImage(
	VehicleImageAngle angle,
	String odometerReading,
	boolean redactionApplied,
	long tokensUsed,
	String originalFilename,
	String originalContentType,
	String originalImageUrl,
	String vehicleImageUrl,
	String vehicleImageContentType,
	List<VehicleDamageFinding> findings,
	Instant uploadedAt,
	ImageAnalysisStatus status,
	String errorMessage,
	int retryCount,
	Long analysisDurationMs
) {
}