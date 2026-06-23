package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.api.dto.admin.AssessmentStatisticsResponse;
import com.nedbank.avo.assessor.api.dto.admin.AssessmentSummaryResponse;
import com.nedbank.avo.assessor.api.dto.admin.BulkOperationResponse;
import com.nedbank.avo.assessor.api.dto.admin.ImageAnalysisDetailsResponse;
import com.nedbank.avo.assessor.api.dto.admin.UpdateAssessmentRequest;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AdminAssessmentService {

	/**
	 * Get detailed summary of an assessment including all image statuses
	 */
	AssessmentSummaryResponse getAssessmentDetails(String assessmentId);

	/**
	 * Update assessment vehicle details and metadata
	 */
	AssessmentSummaryResponse updateAssessment(String assessmentId, UpdateAssessmentRequest request);

	/**
	 * Soft delete (archive) an assessment
	 */
	void deleteAssessment(String assessmentId);

	/**
	 * Permanently delete an assessment (use with caution)
	 */
	void permanentlyDeleteAssessment(String assessmentId);

	/**
	 * Get image analysis details including error information
	 */
	ImageAnalysisDetailsResponse getImageAnalysisDetails(String assessmentId, VehicleImageAngle angle);

	/**
	 * Retry analyzing a specific image that previously failed
	 */
	ImageAnalysisDetailsResponse retryImageAnalysis(String assessmentId, VehicleImageAngle angle);

	/**
	 * Bulk retry all failed images across all assessments
	 */
	BulkOperationResponse bulkRetryFailedImages(int maxRetries);

	/**
	 * Bulk delete/archive assessments by status
	 */
	BulkOperationResponse bulkDeleteByStatus(AssessmentStatus status);

	/**
	 * List assessments by status with pagination
	 */
	Page<AssessmentSummaryResponse> getAssessmentsByStatus(AssessmentStatus status, Pageable pageable);

	/**
	 * List all active (non-archived) assessments with pagination
	 */
	Page<AssessmentSummaryResponse> getAllActiveAssessments(Pageable pageable);

	/**
	 * Get assessment statistics and metrics
	 */
	AssessmentStatisticsResponse getStatistics();

	/**
	 * Restore a deleted (archived) assessment
	 */
	AssessmentSummaryResponse restoreAssessment(String assessmentId);
}
