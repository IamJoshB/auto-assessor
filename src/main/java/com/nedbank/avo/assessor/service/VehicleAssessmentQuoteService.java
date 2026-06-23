package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.domain.Assessment;

public interface VehicleAssessmentQuoteService {

	boolean shouldGenerateQuote(Assessment assessment);

	GeneratedQuotePaths generateAndStoreQuote(Assessment assessment);

	byte[] getQuotePdf(String assessmentId);

	record GeneratedQuotePaths(String htmlUrl, String pdfUrl) {
	}
}
