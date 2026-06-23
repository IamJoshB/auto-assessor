package com.nedbank.avo.assessor.exception;

public class AssessmentReportNotFoundException extends RuntimeException {

	public AssessmentReportNotFoundException(String assessmentId) {
		super("Generated report PDF not found for assessment: " + assessmentId);
	}
}
