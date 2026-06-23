package com.nedbank.avo.assessor.api.dto;

import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;

import java.time.Instant;
import java.util.List;

public record VehicleImageResponse(
	VehicleImageAngle angle,
	boolean redactionApplied,
	long tokensUsed,
	String vehicleImageUrl,
	String vehicleImageContentType,
	List<VehicleDamageFinding> findings,
	Instant uploadedAt
) {
}
