package com.nedbank.avo.assessor.api.dto.admin;

import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import jakarta.validation.constraints.NotNull;

public record RetryImageAnalysisRequest(
	@NotNull(message = "Image angle is required") VehicleImageAngle angle
) {
}
