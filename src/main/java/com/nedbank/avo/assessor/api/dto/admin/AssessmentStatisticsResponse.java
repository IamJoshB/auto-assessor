package com.nedbank.avo.assessor.api.dto.admin;

public record AssessmentStatisticsResponse(
	long totalAssessments,
	long completedCount,
	long failedCount,
	long partialCount,
	long inProgressCount,
	long createdCount,
	long archivedCount,
	long averageTokensPerAssessment
) {
}
