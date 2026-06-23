package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

final class AssessmentImageCaptureRules {
	private static final Set<VehicleImageAngle> REQUIRED_CAPTURE_ANGLES = EnumSet.allOf(VehicleImageAngle.class);

	private AssessmentImageCaptureRules() {
	}

	static boolean hasAllRequiredSuccessfulImages(Assessment assessment) {
		if (assessment == null || assessment.getImages() == null) {
			return false;
		}

		Set<VehicleImageAngle> successfulAngles = assessment.getImages().stream()
			.filter(image -> image.status() == ImageAnalysisStatus.SUCCESS)
			.map(AssessmentImage::angle)
			.collect(Collectors.toSet());

		return successfulAngles.containsAll(REQUIRED_CAPTURE_ANGLES);
	}
}