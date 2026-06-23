package com.nedbank.avo.assessor.domain;

public record VehicleDamageFinding(
	String category,
	String severity,
	String summary,
	String panel,
	String recommendedAction
) {
}