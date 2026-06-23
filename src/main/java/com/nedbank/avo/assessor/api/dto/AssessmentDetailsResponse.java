package com.nedbank.avo.assessor.api.dto;

import com.nedbank.avo.assessor.domain.VehicleDetails;

import java.time.Instant;
import java.util.List;

public record AssessmentDetailsResponse(
        String assessmentId,
        VehicleDetails vehicleDetails,
        String odometerReading,
        long totalTokensUsed,
        String licenseDiscImageUrl,
        String licenseDiscImageContentType,
        String reportHtmlUrl,
        String reportPdfUrl,
        String quoteHtmlUrl,
        String quotePdfUrl,
        List<VehicleImageResponse> vehicleImages,
        Instant createdAt,
        Instant updatedAt
) {
}
