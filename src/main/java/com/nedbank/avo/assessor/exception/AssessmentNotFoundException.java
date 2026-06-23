package com.nedbank.avo.assessor.exception;

public class AssessmentNotFoundException extends RuntimeException {

	public AssessmentNotFoundException(String assessmentId) {
		super("Assessment not found: " + assessmentId);
	}
}
