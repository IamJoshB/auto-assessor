package com.nedbank.avo.assessor.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nedbank.avo.assessor.api.dto.admin.AssessmentStatisticsResponse;
import com.nedbank.avo.assessor.api.dto.admin.AssessmentSummaryResponse;
import com.nedbank.avo.assessor.api.dto.admin.BulkOperationResponse;
import com.nedbank.avo.assessor.api.dto.admin.BulkRetryRequest;
import com.nedbank.avo.assessor.api.dto.admin.ImageAnalysisDetailsResponse;
import com.nedbank.avo.assessor.api.dto.admin.RetryImageAnalysisRequest;
import com.nedbank.avo.assessor.api.dto.admin.UpdateAssessmentRequest;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.AssessmentNotFoundException;
import com.nedbank.avo.assessor.service.AdminAssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminAssessmentControllerTest {

	private MockMvc mockMvc;
	private AdminAssessmentService adminAssessmentService;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		adminAssessmentService = mock(AdminAssessmentService.class);
		objectMapper = new ObjectMapper();
		mockMvc = MockMvcBuilders
			.standaloneSetup(new AdminAssessmentController(adminAssessmentService))
			.setControllerAdvice(new ApiExceptionHandler())
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();
	}

	@Test
	void getAssessmentDetailsReturnsAssessmentSummary() throws Exception {
		var summary = new AssessmentSummaryResponse(
			"assessment-1",
			AssessmentStatus.COMPLETED,
			new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of()),
			3,
			3,
			0,
			1000,
			null,
			Instant.parse("2026-06-11T09:00:00Z"),
			Instant.parse("2026-06-11T10:00:00Z")
		);

		given(adminAssessmentService.getAssessmentDetails("assessment-1")).willReturn(summary);

		mockMvc.perform(get("/api/admin/assessments/{assessmentId}", "assessment-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assessmentId").value("assessment-1"))
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.totalImages").value(3))
			.andExpect(jsonPath("$.successfulImages").value(3))
			.andExpect(jsonPath("$.failedImages").value(0))
			.andExpect(jsonPath("$.totalTokensUsed").value(1000));
	}

	@Test
	void getAssessmentDetailsReturns404WhenNotFound() throws Exception {
		willThrow(new AssessmentNotFoundException("assessment-1")).given(adminAssessmentService).getAssessmentDetails("assessment-1");

		mockMvc.perform(get("/api/admin/assessments/{assessmentId}", "assessment-1"))
			.andExpect(status().isNotFound());
	}

	@Test
	void updateAssessmentUpdatesVehicleDetails() throws Exception {
		var updatedDetails = new VehicleDetails("CA123456", "Honda", "Civic", "2023", "Blue", "ENG456", "VIN456", "Passenger", "2027-09-30", Map.of());
		var request = new UpdateAssessmentRequest(updatedDetails, "120000", "Updated after customer verification");
		var response = new AssessmentSummaryResponse(
			"assessment-1",
			AssessmentStatus.COMPLETED,
			updatedDetails,
			3,
			3,
			0,
			1000,
			null,
			Instant.parse("2026-06-11T09:00:00Z"),
			Instant.parse("2026-06-11T11:00:00Z")
		);

		given(adminAssessmentService.updateAssessment("assessment-1", request)).willReturn(response);

		mockMvc.perform(put("/api/admin/assessments/{assessmentId}", "assessment-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.vehicleDetails.make").value("Honda"))
			.andExpect(jsonPath("$.vehicleDetails.model").value("Civic"));
	}

	@Test
	void deleteAssessmentArchivesAssessment() throws Exception {
		mockMvc.perform(delete("/api/admin/assessments/{assessmentId}", "assessment-1"))
			.andExpect(status().isNoContent());
	}

	@Test
	void restoreAssessmentRestoresArchivedAssessment() throws Exception {
		var restored = new AssessmentSummaryResponse(
			"assessment-1",
			AssessmentStatus.COMPLETED,
			new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of()),
			3,
			3,
			0,
			1000,
			null,
			Instant.parse("2026-06-11T09:00:00Z"),
			Instant.parse("2026-06-11T12:00:00Z")
		);

		given(adminAssessmentService.restoreAssessment("assessment-1")).willReturn(restored);

		mockMvc.perform(post("/api/admin/assessments/{assessmentId}/restore", "assessment-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assessmentId").value("assessment-1"))
			.andExpect(jsonPath("$.status").value("COMPLETED"));
	}

	@Test
	void getImageAnalysisDetailsReturnsImageDetails() throws Exception {
		var imageDetails = new ImageAnalysisDetailsResponse(
			VehicleImageAngle.FRONT_VIEW,
			ImageAnalysisStatus.SUCCESS,
			"./storage/assessment-1/front_view/image.jpg",
			MediaType.IMAGE_JPEG_VALUE,
			300,
			0,
			null,
			Instant.parse("2026-06-11T09:30:00Z")
		);

		given(adminAssessmentService.getImageAnalysisDetails("assessment-1", VehicleImageAngle.FRONT_VIEW))
			.willReturn(imageDetails);

		mockMvc.perform(get("/api/admin/assessments/{assessmentId}/images/{angle}", "assessment-1", "FRONT_VIEW"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.angle").value("FRONT_VIEW"))
			.andExpect(jsonPath("$.status").value("SUCCESS"))
			.andExpect(jsonPath("$.tokensUsed").value(300))
			.andExpect(jsonPath("$.retryCount").value(0));
	}

	@Test
	void retryImageAnalysisMarksImageForRetry() throws Exception {
		var request = new RetryImageAnalysisRequest(VehicleImageAngle.REAR_VIEW);
		var retryResponse = new ImageAnalysisDetailsResponse(
			VehicleImageAngle.REAR_VIEW,
			ImageAnalysisStatus.RETRY_PENDING,
			"./storage/assessment-1/rear_view/image.jpg",
			MediaType.IMAGE_JPEG_VALUE,
			250,
			1,
			null,
			Instant.parse("2026-06-11T10:00:00Z")
		);

		given(adminAssessmentService.retryImageAnalysis("assessment-1", VehicleImageAngle.REAR_VIEW))
			.willReturn(retryResponse);

		mockMvc.perform(post("/api/admin/assessments/{assessmentId}/images/{angle}/retry", "assessment-1", "REAR_VIEW")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RETRY_PENDING"))
			.andExpect(jsonPath("$.retryCount").value(1));
	}

	@Test
	void bulkRetryFailedImagesReturnsOperationResponse() throws Exception {
		var request = new BulkRetryRequest(3);
		var response = new BulkOperationResponse(5, 5, 0, "Bulk retry completed: 5 marked for retry, 0 failed");

		given(adminAssessmentService.bulkRetryFailedImages(3)).willReturn(response);

		mockMvc.perform(post("/api/admin/assessments/retry-all-failed")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalProcessed").value(5))
			.andExpect(jsonPath("$.successCount").value(5))
			.andExpect(jsonPath("$.failureCount").value(0));
	}

	@Test
	void getAssessmentsByStatusReturnsPaginatedResults() throws Exception {
		var summary1 = new AssessmentSummaryResponse(
			"assessment-1",
			AssessmentStatus.COMPLETED,
			new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of()),
			3,
			3,
			0,
			1000,
			null,
			Instant.parse("2026-06-11T09:00:00Z"),
			Instant.parse("2026-06-11T10:00:00Z")
		);

		Page<AssessmentSummaryResponse> page = new PageImpl<>(List.of(summary1), PageRequest.of(0, 20), 1);
		given(adminAssessmentService.getAssessmentsByStatus(AssessmentStatus.COMPLETED, PageRequest.of(0, 20)))
			.willReturn(page);

		mockMvc.perform(get("/api/admin/assessments")
				.param("status", "COMPLETED")
				.param("page", "0")
				.param("size", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].assessmentId").value("assessment-1"))
			.andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void bulkDeleteByStatusArchivesMultipleAssessments() throws Exception {
		var response = new BulkOperationResponse(10, 10, 0, "Bulk delete completed: 10 archived, 0 failed");

		given(adminAssessmentService.bulkDeleteByStatus(AssessmentStatus.FAILED)).willReturn(response);

		mockMvc.perform(post("/api/admin/assessments/bulk-delete-by-status")
				.param("status", "FAILED"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalProcessed").value(10))
			.andExpect(jsonPath("$.successCount").value(10));
	}

	@Test
	void getStatisticsReturnsAssessmentMetrics() throws Exception {
		var stats = new AssessmentStatisticsResponse(
			100,
			60,
			15,
			20,
			5,
			0,
			0,
			900
		);

		given(adminAssessmentService.getStatistics()).willReturn(stats);

		mockMvc.perform(get("/api/admin/assessments/statistics"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalAssessments").value(100))
			.andExpect(jsonPath("$.completedCount").value(60))
			.andExpect(jsonPath("$.failedCount").value(15))
			.andExpect(jsonPath("$.partialCount").value(20))
			.andExpect(jsonPath("$.averageTokensPerAssessment").value(900));
	}
}
