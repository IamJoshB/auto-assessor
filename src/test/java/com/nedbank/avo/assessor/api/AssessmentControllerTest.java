package com.nedbank.avo.assessor.api;

import com.nedbank.avo.assessor.api.dto.AssessmentImageUploadResponse;
import com.nedbank.avo.assessor.api.dto.VehicleImageResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentDetailsResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentListItemResponse;
import com.nedbank.avo.assessor.api.dto.CreateAssessmentResponse;
import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.AssessmentNotFoundException;
import com.nedbank.avo.assessor.exception.AssessmentQuoteNotFoundException;
import com.nedbank.avo.assessor.exception.AssessmentReportNotFoundException;
import com.nedbank.avo.assessor.service.AssessmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssessmentControllerTest {

	private MockMvc mockMvc;

	private AssessmentService assessmentService;

	@BeforeEach
	void setUp() {
		assessmentService = mock(AssessmentService.class);
		mockMvc = MockMvcBuilders
			.standaloneSetup(new AssessmentController(assessmentService))
			.setControllerAdvice(new ApiExceptionHandler())
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();
	}

	@Test
	void createsAssessmentFromLicenseDiscImage() throws Exception {
		given(assessmentService.createAssessment(any())).willReturn(new CreateAssessmentResponse(
			"assessment-1",
			new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of("licenceNumber", "CA123456")),
			"./local-storage/assessment-1/license_disc/original/disc.jpg",
			MediaType.IMAGE_JPEG_VALUE,
			null,
			null,
			null,
			null,
			412,
			Instant.parse("2026-06-11T09:30:00Z")
		));

		MockMultipartFile image = new MockMultipartFile("licenseDiscImage", "disc.jpg", MediaType.IMAGE_JPEG_VALUE, "image-bytes".getBytes());

		mockMvc.perform(multipart("/api/assessments")
				.file(image)
				.param("vinNumber", "VIN123"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.assessmentId").value("assessment-1"))
			.andExpect(jsonPath("$.vehicleDetails.vinNumber").value("VIN123"))
			.andExpect(jsonPath("$.licenseDiscImageUrl").value("./local-storage/assessment-1/license_disc/original/disc.jpg"))
			.andExpect(jsonPath("$.totalTokensUsed").value(412))
			.andExpect(jsonPath("$.vehicleDetails.make").value("Toyota"));
	}

	@Test
	void uploadsVehicleImageByAngle() throws Exception {
		given(assessmentService.analyzeVehicleImage(eq("assessment-1"), eq(VehicleImageAngle.FRONT_VIEW), any())).willReturn(new AssessmentImageUploadResponse(
			"assessment-1",
			VehicleImageAngle.FRONT_VIEW,
			"",
			581,
			true,
			List.of(new VehicleDamageFinding("scratch", "minor", "Scratch on bumper", "Front bumper", "fix")),
			"./local-storage/assessment-1/front_view/original/a.jpg",
			"./local-storage/assessment-1/front_view/damage_image/b.jpg",
			MediaType.IMAGE_JPEG_VALUE,
			Instant.parse("2026-06-11T10:00:00Z")
		));

		MockMultipartFile image = new MockMultipartFile("image", "front.jpg", MediaType.IMAGE_JPEG_VALUE, "image-bytes".getBytes());

		mockMvc.perform(multipart("/api/assessments/{assessmentId}/images", "assessment-1")
				.file(image)
				.param("angle", VehicleImageAngle.FRONT_VIEW.name()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.assessmentId").value("assessment-1"))
			.andExpect(jsonPath("$.angle").value("FRONT_VIEW"))
			.andExpect(jsonPath("$.totalTokensUsed").value(581))
			.andExpect(jsonPath("$.redactionApplied").value(true))
			.andExpect(jsonPath("$.findings[0].category").value("scratch"));
	}

	@Test
	void returnsNotFoundWhenAssessmentDoesNotExist() throws Exception {
		given(assessmentService.analyzeVehicleImage(eq("missing"), eq(VehicleImageAngle.REAR_VIEW), any()))
			.willThrow(new AssessmentNotFoundException("missing"));

		MockMultipartFile image = new MockMultipartFile("image", "rear.jpg", MediaType.IMAGE_JPEG_VALUE, "image-bytes".getBytes());

		mockMvc.perform(multipart("/api/assessments/{assessmentId}/images", "missing")
				.file(image)
				.param("angle", VehicleImageAngle.REAR_VIEW.name()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Assessment not found: missing"));
	}

	@Test
	void listsAssessmentsWithPagingAndSorting() throws Exception {
		Page<AssessmentListItemResponse> page = new PageImpl<>(
			List.of(new AssessmentListItemResponse(
				"assessment-1",
				new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of()),
				"",
				777,
				"./local-storage/assessment-1/report/vehicle-assessment-report.html",
				"./local-storage/assessment-1/report/vehicle-assessment-report.pdf",
				"./local-storage/assessment-1/quote/vehicle-replacement-quote.html",
				"./local-storage/assessment-1/quote/vehicle-replacement-quote.pdf",
				"./local-storage/assessment-1/front_driver_corner/damage_image/fdc.jpg",
				MediaType.IMAGE_JPEG_VALUE,
				Instant.parse("2026-06-11T09:30:00Z"),
				Instant.parse("2026-06-11T10:30:00Z")
			)),
			PageRequest.of(0, 10),
			1
		);

		given(assessmentService.getAssessments(any())).willReturn(page);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assessments")
				.param("page", "0")
				.param("size", "10")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].assessmentId").value("assessment-1"))
			.andExpect(jsonPath("$.content[0].reportPdfUrl").value("./local-storage/assessment-1/report/vehicle-assessment-report.pdf"))
			.andExpect(jsonPath("$.content[0].frontDriverCornerVehicleImageUrl").value("./local-storage/assessment-1/front_driver_corner/damage_image/fdc.jpg"))
			.andExpect(jsonPath("$.content[0].frontDriverCornerVehicleImageContentType").value(MediaType.IMAGE_JPEG_VALUE))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void returnsSingleAssessmentWithAllVehicleImagesAndLicenseDiscImage() throws Exception {
		given(assessmentService.getAssessmentById("assessment-1")).willReturn(new AssessmentDetailsResponse(
			"assessment-1",
			new VehicleDetails("CA123456", "Toyota", "Corolla", "2022", "White", "ENG123", "VIN123", "Passenger", "2026-09-30", Map.of()),
			"",
			901,
			"./local-storage/assessment-1/license_disc/original/disc.jpg",
			MediaType.IMAGE_JPEG_VALUE,
			"./local-storage/assessment-1/report/vehicle-assessment-report.html",
			"./local-storage/assessment-1/report/vehicle-assessment-report.pdf",
			"./local-storage/assessment-1/quote/vehicle-replacement-quote.html",
			"./local-storage/assessment-1/quote/vehicle-replacement-quote.pdf",
			List.of(
				new VehicleImageResponse(
					VehicleImageAngle.FRONT_DRIVER_CORNER,
					true,
					350,
					"./local-storage/assessment-1/front_driver_corner/vehicle_image/fdc.jpg",
					MediaType.IMAGE_JPEG_VALUE,
					List.of(new VehicleDamageFinding("scratch", "minor", "Scratch on hood", "Front hood", "fix")),
					Instant.parse("2026-06-11T10:00:00Z")
				),
				new VehicleImageResponse(
					VehicleImageAngle.REAR_VIEW,
					false,
					551,
					"./local-storage/assessment-1/rear_view/vehicle_image/rear.jpg",
					MediaType.IMAGE_JPEG_VALUE,
					List.of(),
					Instant.parse("2026-06-11T10:05:00Z")
				)
			),
			Instant.parse("2026-06-11T09:30:00Z"),
			Instant.parse("2026-06-11T10:30:00Z")
		));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assessments/{assessmentId}", "assessment-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assessmentId").value("assessment-1"))
			.andExpect(jsonPath("$.reportPdfUrl").value("./local-storage/assessment-1/report/vehicle-assessment-report.pdf"))
			.andExpect(jsonPath("$.licenseDiscImageUrl").value("./local-storage/assessment-1/license_disc/original/disc.jpg"))
			.andExpect(jsonPath("$.vehicleImages[0].angle").value("FRONT_DRIVER_CORNER"))
			.andExpect(jsonPath("$.vehicleImages[0].vehicleImageUrl").value("./local-storage/assessment-1/front_driver_corner/vehicle_image/fdc.jpg"))
			.andExpect(jsonPath("$.vehicleImages.length()").value(2));
	}

	@Test
	void downloadsGeneratedAssessmentPdf() throws Exception {
		byte[] pdf = "pdf-content".getBytes();
		given(assessmentService.getAssessmentReportPdf("assessment-1")).willReturn(pdf);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assessments/{assessmentId}/report.pdf", "assessment-1"))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"assessment-1-vehicle-assessment-report.pdf\""));
	}

	@Test
	void returnsNotFoundWhenGeneratedReportDoesNotExist() throws Exception {
		given(assessmentService.getAssessmentReportPdf("assessment-1"))
			.willThrow(new AssessmentReportNotFoundException("assessment-1"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assessments/{assessmentId}/report.pdf", "assessment-1"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Generated report PDF not found for assessment: assessment-1"));
	}

	@Test
	void downloadsGeneratedQuotePdf() throws Exception {
		byte[] pdf = "quote-pdf-content".getBytes();
		given(assessmentService.getAssessmentQuotePdf("assessment-1")).willReturn(pdf);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assessments/{assessmentId}/quote.pdf", "assessment-1"))
			.andExpect(status().isOk())
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"assessment-1-vehicle-replacement-quote.pdf\""));
	}

	@Test
	void returnsNotFoundWhenGeneratedQuoteDoesNotExist() throws Exception {
		given(assessmentService.getAssessmentQuotePdf("assessment-1"))
			.willThrow(new AssessmentQuoteNotFoundException("assessment-1"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/assessments/{assessmentId}/quote.pdf", "assessment-1"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Generated quote PDF not found for assessment: assessment-1"));
	}
}