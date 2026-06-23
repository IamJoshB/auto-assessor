package com.nedbank.avo.assessor.api.dto;

import com.nedbank.avo.assessor.domain.VehicleDetails;

import java.time.Instant;

public record CreateAssessmentResponse(
	String assessmentId,
	VehicleDetails vehicleDetails,
	String licenseDiscImageUrl,
	String licenseDiscImageContentType,
	String reportHtmlUrl,
	String reportPdfUrl,
	String quoteHtmlUrl,
	String quotePdfUrl,
	long totalTokensUsed,
	Instant createdAt
) {
}