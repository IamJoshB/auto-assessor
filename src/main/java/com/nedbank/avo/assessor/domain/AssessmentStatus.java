package com.nedbank.avo.assessor.domain;

public enum AssessmentStatus {
	CREATED,      // Initial state after license disc upload
	IN_PROGRESS,  // At least one image is being analyzed
	COMPLETED,    // All images successfully analyzed
	PARTIAL,      // Some images analyzed, others failed
	FAILED,       // Critical failure in assessment
	ARCHIVED      // Soft deleted
}
