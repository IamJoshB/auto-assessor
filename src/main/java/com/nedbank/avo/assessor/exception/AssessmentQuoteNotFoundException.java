package com.nedbank.avo.assessor.exception;

public class AssessmentQuoteNotFoundException extends RuntimeException {

	public AssessmentQuoteNotFoundException(String assessmentId) {
		super("Generated quote PDF not found for assessment: " + assessmentId);
	}
}
