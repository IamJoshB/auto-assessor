package com.nedbank.avo.assessor.api;

import com.nedbank.avo.assessor.api.dto.AssessmentImageUploadResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentDetailsResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentListItemResponse;
import com.nedbank.avo.assessor.api.dto.CreateAssessmentResponse;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.service.AssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.web.PageableDefault;

@Tag(name = "Assessments", description = "Vehicle damage assessment operations")
@Validated
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

	private final AssessmentService assessmentService;

	@Autowired
	public AssessmentController(AssessmentService assessmentService) {
		this.assessmentService = assessmentService;
	}

	@Operation(summary = "Create assessment", description = "Creates a new vehicle assessment by extracting details from a license disc image")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Assessment created successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (validation or illegal argument)"),
		@ApiResponse(responseCode = "502", description = "OpenAI integration failure")
	})
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public CreateAssessmentResponse createAssessment(
		@Parameter(description = "License disc image used to extract vehicle details")
		@RequestPart("licenseDiscImage") MultipartFile licenseDiscImage
	) {
		return assessmentService.createAssessment(licenseDiscImage);
	}

	@Operation(
		summary = "Upload and analyze vehicle image",
		description = "Uploads one vehicle image for the requested angle, runs damage analysis, and returns findings plus stored image locations."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Image analyzed successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (validation or illegal argument)"),
		@ApiResponse(responseCode = "404", description = "Assessment not found"),
		@ApiResponse(responseCode = "409", description = "Duplicate image angle for this assessment"),
		@ApiResponse(responseCode = "502", description = "OpenAI integration failure")
	})
	@PostMapping(path = "/{assessmentId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public AssessmentImageUploadResponse analyzeVehicleImage(
		@Parameter(description = "Assessment identifier") @PathVariable("assessmentId") String assessmentId,
		@Parameter(description = "Vehicle image file") @RequestPart("image") MultipartFile image,
		@Parameter(description = "Camera angle represented by the uploaded image") @RequestParam("angle") VehicleImageAngle angle
	) {
		return assessmentService.analyzeVehicleImage(assessmentId, angle, image);
	}

	@Operation(
		summary = "List assessments",
		description = "Returns paginated assessments ordered by pageable input. Each list item includes a FRONT_DRIVER_CORNER preview image when available."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Assessments returned successfully"),
		@ApiResponse(responseCode = "400", description = "Bad request (invalid paging parameters)")
	})
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public Page<AssessmentListItemResponse> getAssessments(
		@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
	) {
		return assessmentService.getAssessments(pageable);
	}

	@Operation(
		summary = "Get assessment by ID",
		description = "Returns a full assessment view including vehicle details, all analyzed images, and generated report/quote paths."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Assessment returned successfully"),
		@ApiResponse(responseCode = "404", description = "Assessment not found")
	})
	@GetMapping(path = "/{assessmentId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public AssessmentDetailsResponse getAssessmentById(
		@Parameter(description = "ID of the assessment") @PathVariable("assessmentId") String assessmentId
	) {
		return assessmentService.getAssessmentById(assessmentId);
	}

	@Operation(
		summary = "Download report PDF",
		description = "Downloads the generated vehicle assessment report PDF for the selected assessment."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Report downloaded successfully"),
		@ApiResponse(responseCode = "404", description = "Assessment or generated report not found")
	})
	@GetMapping(path = "/{assessmentId}/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> downloadAssessmentReportPdf(
		@Parameter(description = "ID of the assessment") @PathVariable("assessmentId") String assessmentId
	) {
		byte[] pdfBytes = assessmentService.getAssessmentReportPdf(assessmentId);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + assessmentId + "-vehicle-assessment-report.pdf\"")
			.body(pdfBytes);
	}

	@Operation(
		summary = "Download quote PDF",
		description = "Downloads the generated vehicle replacement quote PDF for the selected assessment."
	)
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Quote downloaded successfully"),
		@ApiResponse(responseCode = "404", description = "Assessment or generated quote not found")
	})
	@GetMapping(path = "/{assessmentId}/quote.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> downloadAssessmentQuotePdf(
		@Parameter(description = "ID of the assessment") @PathVariable("assessmentId") String assessmentId
	) {
		byte[] pdfBytes = assessmentService.getAssessmentQuotePdf(assessmentId);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + assessmentId + "-vehicle-replacement-quote.pdf\"")
			.body(pdfBytes);
	}
}