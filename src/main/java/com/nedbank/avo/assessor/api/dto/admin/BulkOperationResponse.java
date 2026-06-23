package com.nedbank.avo.assessor.api.dto.admin;

public record BulkOperationResponse(
	int totalProcessed,
	int successCount,
	int failureCount,
	String message
) {
}
