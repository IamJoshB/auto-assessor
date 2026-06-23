package com.nedbank.avo.assessor.exception;

import com.nedbank.avo.assessor.domain.VehicleImageAngle;

public class DuplicateVehicleAngleException extends RuntimeException {

	public DuplicateVehicleAngleException(String assessmentId, VehicleImageAngle angle) {
		super("Assessment " + assessmentId + " already contains an image for angle " + angle);
	}
}
