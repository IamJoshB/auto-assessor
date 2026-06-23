package com.nedbank.avo.assessor.api.dto;

import com.nedbank.avo.assessor.domain.VehicleDetails;

import java.time.Instant;

public record AssessmentListItemResponse(
        String assessmentId,
        VehicleDetails vehicleDetails,
        String odometerReading,
        long totalTokensUsed,
        String reportHtmlUrl,
        String reportPdfUrl,
        String quoteHtmlUrl,
        String quotePdfUrl,
        String frontDriverCornerVehicleImageUrl,
        String frontDriverCornerVehicleImageContentType,
        Instant createdAt,
        Instant updatedAt
) {
}
