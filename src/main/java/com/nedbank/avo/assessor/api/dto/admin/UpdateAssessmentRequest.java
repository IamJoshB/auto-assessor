package com.nedbank.avo.assessor.api.dto.admin;

import com.nedbank.avo.assessor.domain.VehicleDetails;
import jakarta.validation.constraints.NotNull;

public record UpdateAssessmentRequest(
	@NotNull(message = "Vehicle details are required") VehicleDetails vehicleDetails,
	String odometerReading,
	String notes
) {
}
