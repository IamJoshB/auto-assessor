package com.nedbank.avo.assessor.api.dto.admin;

public record BulkRetryRequest(
	int maxRetries
) {
	public BulkRetryRequest {
		if (maxRetries <= 0) {
			throw new IllegalArgumentException("maxRetries must be greater than 0");
		}
	}
}
