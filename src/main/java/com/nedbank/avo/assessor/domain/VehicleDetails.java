package com.nedbank.avo.assessor.domain;

import java.util.Map;

public record VehicleDetails(
	String registrationNumber,
	String make,
	String model,
	String modelYear,
	String colour,
	String engineNumber,
	String vinNumber,
	String vehicleCategory,
	String expiryDate,
	Map<String, String> extractedFields
) {
}