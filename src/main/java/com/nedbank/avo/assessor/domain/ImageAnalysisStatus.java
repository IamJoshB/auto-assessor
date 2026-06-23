package com.nedbank.avo.assessor.domain;

public enum ImageAnalysisStatus {
	PENDING,           // Waiting to be analyzed
	IN_PROGRESS,       // Currently being analyzed
	SUCCESS,           // Successfully analyzed
	FAILED,            // Analysis failed
	RETRY_PENDING      // Marked for retry after failure
}
