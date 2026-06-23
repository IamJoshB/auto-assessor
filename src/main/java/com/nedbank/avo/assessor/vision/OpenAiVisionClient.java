package com.nedbank.avo.assessor.vision;

import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;

import java.util.List;

public interface OpenAiVisionClient {

	VehicleDetailsResult extractVehicleDetails(String contentType, byte[] licenseDiscImage);

	record VehicleDetailsResult(VehicleDetails vehicleDetails, long tokensUsed) {
	}

	DamageAnalysisResult analyzeVehicleDamage(VehicleImageAngle angle, VehicleDetails vehicleDetails, String contentType, byte[] vehicleImage);

	record DamageAnalysisResult(
		String odometerReading,
		long tokensUsed,
		boolean redactionApplied,
		String vehicleImageContentType,
		byte[] vehicleImageBytes,
		List<DamageFindingResult> findings
	) {
	}

	record DamageFindingResult(
		String category,
		String severity,
		String summary,
		String panel,
		String recommendedAction
	) {
	}
}
