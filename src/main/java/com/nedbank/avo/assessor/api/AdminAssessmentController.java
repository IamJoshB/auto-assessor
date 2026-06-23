package com.nedbank.avo.assessor.api;

import com.nedbank.avo.assessor.api.dto.admin.AssessmentStatisticsResponse;
import com.nedbank.avo.assessor.api.dto.admin.AssessmentSummaryResponse;
import com.nedbank.avo.assessor.api.dto.admin.BulkOperationResponse;
import com.nedbank.avo.assessor.api.dto.admin.BulkRetryRequest;
import com.nedbank.avo.assessor.api.dto.admin.ImageAnalysisDetailsResponse;
import com.nedbank.avo.assessor.api.dto.admin.RetryImageAnalysisRequest;
import com.nedbank.avo.assessor.api.dto.admin.UpdateAssessmentRequest;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.service.AdminAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin API for managing assessments.
 * Provides operations for viewing, updating, retrying, and managing assessment lifecycle.
 */
@RestController
@RequestMapping("/api/admin/assessments")
@Tag(name = "Admin Assessment Management", description = "Endpoints for administrative assessment operations")
public class AdminAssessmentController {

	private final AdminAssessmentService adminAssessmentService;

	public AdminAssessmentController(AdminAssessmentService adminAssessmentService) {
		this.adminAssessmentService = adminAssessmentService;
	}

	@GetMapping("/{assessmentId}")
	@Operation(
		summary = "Get assessment details",
		description = "Retrieves a single assessment summary for admin workflows, including status, image counters, token usage, and timestamps."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Assessment summary returned successfully"),
		@ApiResponse(responseCode = "404", description = "Assessment not found")
	})
	public ResponseEntity<AssessmentSummaryResponse> getAssessmentDetails(
		@Parameter(description = "Unique assessment identifier") @PathVariable String assessmentId
	) {
		return ResponseEntity.ok(adminAssessmentService.getAssessmentDetails(assessmentId));
	}

	@PutMapping("/{assessmentId}")
	@Operation(
		summary = "Update assessment",
		description = "Updates editable assessment data such as vehicle details and odometer reading."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Assessment updated successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (validation or illegal argument)"),
		@ApiResponse(responseCode = "404", description = "Assessment not found")
	})
	public ResponseEntity<AssessmentSummaryResponse> updateAssessment(
		@Parameter(description = "Unique assessment identifier") @PathVariable String assessmentId,
		@Valid @RequestBody UpdateAssessmentRequest request
	) {
		return ResponseEntity.ok(adminAssessmentService.updateAssessment(assessmentId, request));
	}

	@DeleteMapping("/{assessmentId}")
	@Operation(
		summary = "Archive assessment",
		description = "Soft deletes an assessment by marking it as archived. The record remains available for restore operations."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "Assessment archived successfully"),
		@ApiResponse(responseCode = "404", description = "Assessment not found")
	})
	public ResponseEntity<Void> deleteAssessment(
		@Parameter(description = "Unique assessment identifier") @PathVariable String assessmentId
	) {
		adminAssessmentService.deleteAssessment(assessmentId);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	@PostMapping("/{assessmentId}/restore")
	@Operation(
		summary = "Restore assessment",
		description = "Restores a previously archived assessment back into active workflows."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Assessment restored successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (validation or illegal argument)"),
		@ApiResponse(responseCode = "404", description = "Assessment not found")
	})
	public ResponseEntity<AssessmentSummaryResponse> restoreAssessment(
		@Parameter(description = "Unique assessment identifier") @PathVariable String assessmentId
	) {
		return ResponseEntity.ok(adminAssessmentService.restoreAssessment(assessmentId));
	}

	@GetMapping("/{assessmentId}/images/{angle}")
	@Operation(
		summary = "Get image analysis details",
		description = "Returns analysis state for a specific image angle, including status, token usage, retry count, and error information."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Image analysis details returned successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (validation or illegal argument)"),
		@ApiResponse(responseCode = "404", description = "Assessment not found")
	})
	public ResponseEntity<ImageAnalysisDetailsResponse> getImageAnalysisDetails(
		@Parameter(description = "Unique assessment identifier") @PathVariable String assessmentId,
		@Parameter(description = "Vehicle image angle to inspect") @PathVariable VehicleImageAngle angle
	) {
		return ResponseEntity.ok(adminAssessmentService.getImageAnalysisDetails(assessmentId, angle));
	}

	@PostMapping("/{assessmentId}/images/{angle}/retry")
	@Operation(
		summary = "Retry image analysis",
		description = "Marks an image angle for reprocessing when the previous analysis failed."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Image marked for retry successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (validation or illegal argument)"),
		@ApiResponse(responseCode = "404", description = "Assessment not found")
	})
	public ResponseEntity<ImageAnalysisDetailsResponse> retryImageAnalysis(
		@Parameter(description = "Unique assessment identifier") @PathVariable String assessmentId,
		@Parameter(description = "Vehicle image angle to retry") @PathVariable VehicleImageAngle angle,
		@Valid @RequestBody RetryImageAnalysisRequest request
	) {
		return ResponseEntity.ok(adminAssessmentService.retryImageAnalysis(assessmentId, angle));
	}

	@PostMapping("/retry-all-failed")
	@Operation(
		summary = "Bulk retry failed images",
		description = "Marks all failed images across non-archived assessments as retry-pending, limited by max retry count."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Bulk retry operation completed"),
		@ApiResponse(responseCode = "400", description = "Bad request (validation or illegal argument)")
	})
	public ResponseEntity<BulkOperationResponse> bulkRetryFailedImages(
		@Valid @RequestBody BulkRetryRequest request
	) {
		return ResponseEntity.ok(adminAssessmentService.bulkRetryFailedImages(request.maxRetries()));
	}

	@GetMapping
	@Operation(
		summary = "List assessments",
		description = "Returns paginated admin assessment summaries. When status is omitted, all active (non-archived) assessments are returned."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Assessment page returned successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (invalid paging or filter parameters)")
	})
	public ResponseEntity<Page<AssessmentSummaryResponse>> getAssessmentsByStatus(
		@Parameter(description = "Optional status filter")
		@RequestParam(required = false) AssessmentStatus status,
		@Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
		@Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
	) {
		Pageable pageable = PageRequest.of(page, size);
		if (status == null) {
			// Return all non-archived assessments
			return ResponseEntity.ok(adminAssessmentService.getAllActiveAssessments(pageable));
		}
		return ResponseEntity.ok(adminAssessmentService.getAssessmentsByStatus(status, pageable));
	}

	@PostMapping("/bulk-delete-by-status")
	@Operation(
		summary = "Bulk archive by status",
		description = "Archives all non-archived assessments currently in the requested status."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Bulk archive operation completed"),
		@ApiResponse(responseCode = "400", description = "Bad request (invalid status parameter)")
	})
	public ResponseEntity<BulkOperationResponse> bulkDeleteByStatus(
		@Parameter(description = "Status used to select assessments to archive")
		@RequestParam AssessmentStatus status
	) {
		return ResponseEntity.ok(adminAssessmentService.bulkDeleteByStatus(status));
	}

	@GetMapping("/statistics")
	@Operation(
		summary = "Get assessment statistics",
		description = "Returns aggregate assessment metrics across statuses, including totals and average token usage."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Statistics returned successfully")
	})
	public ResponseEntity<AssessmentStatisticsResponse> getStatistics() {
		return ResponseEntity.ok(adminAssessmentService.getStatistics());
	}
}
