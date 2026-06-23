package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.api.dto.AssessmentImageUploadResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentDetailsResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentListItemResponse;
import com.nedbank.avo.assessor.api.dto.VehicleImageResponse;
import com.nedbank.avo.assessor.api.dto.CreateAssessmentResponse;
import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentImage;
import com.nedbank.avo.assessor.domain.ImageAnalysisStatus;
import com.nedbank.avo.assessor.domain.VehicleDamageFinding;
import com.nedbank.avo.assessor.domain.VehicleDetails;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.AssessmentNotFoundException;
import com.nedbank.avo.assessor.exception.AssessmentReportNotFoundException;
import com.nedbank.avo.assessor.exception.DuplicateVehicleAngleException;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import com.nedbank.avo.assessor.repository.AssessmentRepository;
import com.nedbank.avo.assessor.storage.S3ImageStorageService;
import com.nedbank.avo.assessor.storage.StoredImage;
import com.nedbank.avo.assessor.vision.OpenAiVisionClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultAssessmentService implements AssessmentService {

	private final AssessmentRepository assessmentRepository;
	private final OpenAiVisionClient openAiVisionClient;
	private final S3ImageStorageService s3ImageStorageService;
	private final VehicleAssessmentReportService vehicleAssessmentReportService;
	private final VehicleAssessmentQuoteService vehicleAssessmentQuoteService;
	private final Clock clock;

	public DefaultAssessmentService(
			AssessmentRepository assessmentRepository,
			OpenAiVisionClient openAiVisionClient,
			S3ImageStorageService s3ImageStorageService,
			VehicleAssessmentReportService vehicleAssessmentReportService,
			VehicleAssessmentQuoteService vehicleAssessmentQuoteService,
			Clock clock) {
		this.assessmentRepository = assessmentRepository;
		this.openAiVisionClient = openAiVisionClient;
		this.s3ImageStorageService = s3ImageStorageService;
		this.vehicleAssessmentReportService = vehicleAssessmentReportService;
		this.vehicleAssessmentQuoteService = vehicleAssessmentQuoteService;
		this.clock = clock;
	}

	@Override
	public CreateAssessmentResponse createAssessment(MultipartFile licenseDiscImage) {
		byte[] imageBytes = requireImage(licenseDiscImage, "licenseDiscImage");
		OpenAiVisionClient.VehicleDetailsResult vehicleDetailsResult = openAiVisionClient.extractVehicleDetails(
			licenseDiscImage.getContentType(),
			imageBytes);
		VehicleDetails vehicleDetails = vehicleDetailsResult.vehicleDetails();
		Instant now = Instant.now(clock);
		Assessment assessment = new Assessment(vehicleDetails, now, now);
		assessment.setTotalTokensUsed(vehicleDetailsResult.tokensUsed());
		Assessment savedAssessment = assessmentRepository.save(assessment);

		StoredImage storedLicenseDisc = s3ImageStorageService.storeLicenseDisc(
				savedAssessment.getId(),
				licenseDiscImage.getContentType(),
				imageBytes);
		savedAssessment.setLicenseDiscImageUrl(storedLicenseDisc.url());
		savedAssessment.setLicenseDiscImageContentType(storedLicenseDisc.contentType());
		assessmentRepository.save(savedAssessment);

		return new CreateAssessmentResponse(
				savedAssessment.getId(),
				savedAssessment.getVehicleDetails(),
				savedAssessment.getLicenseDiscImageUrl(),
				savedAssessment.getLicenseDiscImageContentType(),
				savedAssessment.getReportHtmlUrl(),
				savedAssessment.getReportPdfUrl(),
				savedAssessment.getQuoteHtmlUrl(),
				savedAssessment.getQuotePdfUrl(),
				savedAssessment.getTotalTokensUsed(),
				savedAssessment.getCreatedAt());
	}

	@Override
	public AssessmentImageUploadResponse analyzeVehicleImage(String assessmentId, VehicleImageAngle angle,
			MultipartFile image) {
		long analysisStartTime = System.currentTimeMillis();

		Assessment assessment = findAssessment(assessmentId);
		ensureAngleNotUploaded(assessment, assessmentId, angle);

		byte[] imageBytes = requireImage(image, "image");
		OpenAiVisionClient.DamageAnalysisResult damageAnalysis = openAiVisionClient.analyzeVehicleDamage(
				angle,
				assessment.getVehicleDetails(),
				image.getContentType(),
				imageBytes);
		long analysisDurationMs = System.currentTimeMillis() - analysisStartTime;
		List<VehicleDamageFinding> findings = mapFindings(damageAnalysis);
		String odometerReading = normalizeOdometerReading(damageAnalysis.odometerReading());
		StoredImage storedOriginal = s3ImageStorageService.storeOriginal(assessmentId,
				angle, image.getContentType(), imageBytes);
		StoredImage storedVehicleImage = s3ImageStorageService.storeVehicleImage(
				assessmentId,
				angle,
				damageAnalysis.vehicleImageContentType(),
				damageAnalysis.vehicleImageBytes());

		Instant uploadedAt = Instant.now(clock);
		assessment.getImages().add(new AssessmentImage(
				angle,
				odometerReading,
				damageAnalysis.redactionApplied(),
				damageAnalysis.tokensUsed(),
				image.getOriginalFilename(),
				storedOriginal.contentType(),
				storedOriginal.url(),
				storedVehicleImage.url(),
				storedVehicleImage.contentType(),
				findings,
				uploadedAt,
				ImageAnalysisStatus.SUCCESS,
				null,
				0,
				analysisDurationMs));

		if (angle == VehicleImageAngle.ODOMETER) {
			assessment.setOdometerReading(odometerReading);
		}

		assessment.setTotalTokensUsed(assessment.getTotalTokensUsed() + damageAnalysis.tokensUsed());
		assessment.setStatus(AssessmentStatusResolver.resolve(assessment));

		assessment.setUpdatedAt(uploadedAt);
		assessmentRepository.save(assessment);

		updateGeneratedArtifacts(assessment);

		assessment.setUpdatedAt(Instant.now(clock));
		assessmentRepository.save(assessment);

		return new AssessmentImageUploadResponse(
				assessment.getId(),
				angle,
				odometerReading,
				assessment.getTotalTokensUsed(),
				damageAnalysis.redactionApplied(),
				findings,
				storedOriginal.url(),
				storedVehicleImage.url(),
				storedVehicleImage.contentType(),
				uploadedAt);
	}

	@Override
	public Page<AssessmentListItemResponse> getAssessments(Pageable pageable) {
		return assessmentRepository.findAll(pageable)
				.map(this::toAssessmentListItemResponse);
	}

	@Override
	public AssessmentDetailsResponse getAssessmentById(String assessmentId) {
		Assessment assessment = findAssessment(assessmentId);

		List<VehicleImageResponse> vehicleImages = assessment.getImages().stream()
				.map(this::toVehicleImageResponse)
				.toList();

		return new AssessmentDetailsResponse(
				assessment.getId(),
				assessment.getVehicleDetails(),
				normalizeOdometerReading(assessment.getOdometerReading()),
				assessment.getTotalTokensUsed(),
				assessment.getLicenseDiscImageUrl(),
				assessment.getLicenseDiscImageContentType(),
				assessment.getReportHtmlUrl(),
				assessment.getReportPdfUrl(),
				assessment.getQuoteHtmlUrl(),
				assessment.getQuotePdfUrl(),
				vehicleImages,
				assessment.getCreatedAt(),
				assessment.getUpdatedAt());
	}

	private AssessmentListItemResponse toAssessmentListItemResponse(Assessment assessment) {
		AssessmentImage frontDriverCornerImage = assessment.getImages().stream()
				.filter(image -> image.angle() == VehicleImageAngle.FRONT_DRIVER_CORNER)
				.findFirst()
				.orElse(null);

		String vehicleImageUrl = frontDriverCornerImage == null ? null : frontDriverCornerImage.vehicleImageUrl();
		String vehicleImageContentType = frontDriverCornerImage == null ? null
				: frontDriverCornerImage.vehicleImageContentType();

		return new AssessmentListItemResponse(
				assessment.getId(),
				assessment.getVehicleDetails(),
				normalizeOdometerReading(assessment.getOdometerReading()),
				assessment.getTotalTokensUsed(),
				assessment.getReportHtmlUrl(),
				assessment.getReportPdfUrl(),
				assessment.getQuoteHtmlUrl(),
				assessment.getQuotePdfUrl(),
				vehicleImageUrl,
				vehicleImageContentType,
				assessment.getCreatedAt(),
				assessment.getUpdatedAt());
	}

	@Override
	public byte[] getAssessmentReportPdf(String assessmentId) {
		Assessment assessment = findAssessment(assessmentId);

		String reportPdfUrl = assessment.getReportPdfUrl();
		if (reportPdfUrl == null || reportPdfUrl.isBlank()) {
			throw new AssessmentReportNotFoundException(assessmentId);
		}

		return s3ImageStorageService.loadFile(reportPdfUrl);
	}

	@Override
	public byte[] getAssessmentQuotePdf(String assessmentId) {
		Assessment assessment = findAssessment(assessmentId);

		String quotePdfUrl = assessment.getQuotePdfUrl();
		if (quotePdfUrl == null || quotePdfUrl.isBlank()) {
			return vehicleAssessmentQuoteService.getQuotePdf(assessmentId);
		}

		return s3ImageStorageService.loadFile(quotePdfUrl);
	}

	private VehicleImageResponse toVehicleImageResponse(AssessmentImage image) {
		return new VehicleImageResponse(
				image.angle(),
				image.redactionApplied(),
				image.tokensUsed(),
				image.vehicleImageUrl(),
				image.vehicleImageContentType(),
				image.findings(),
				image.uploadedAt());
	}

	private Assessment findAssessment(String assessmentId) {
		return assessmentRepository.findById(assessmentId)
			.orElseThrow(() -> new AssessmentNotFoundException(assessmentId));
	}

	private void ensureAngleNotUploaded(Assessment assessment, String assessmentId, VehicleImageAngle angle) {
		boolean angleAlreadyExists = assessment.getImages().stream()
			.anyMatch(existing -> existing.angle() == angle);
		if (angleAlreadyExists) {
			throw new DuplicateVehicleAngleException(assessmentId, angle);
		}
	}

	private void updateGeneratedArtifacts(Assessment assessment) {
		if (vehicleAssessmentReportService.shouldGenerateReport(assessment)) {
			VehicleAssessmentReportService.GeneratedReportPaths generatedReportPaths = vehicleAssessmentReportService
				.generateAndStoreReport(assessment);
			assessment.setReportHtmlUrl(generatedReportPaths.htmlUrl());
			assessment.setReportPdfUrl(generatedReportPaths.pdfUrl());
		}

		if (vehicleAssessmentQuoteService.shouldGenerateQuote(assessment)) {
			VehicleAssessmentQuoteService.GeneratedQuotePaths generatedQuotePaths = vehicleAssessmentQuoteService
				.generateAndStoreQuote(assessment);
			assessment.setQuoteHtmlUrl(generatedQuotePaths.htmlUrl());
			assessment.setQuotePdfUrl(generatedQuotePaths.pdfUrl());
		}
	}

	private List<VehicleDamageFinding> mapFindings(OpenAiVisionClient.DamageAnalysisResult damageAnalysis) {
		List<VehicleDamageFinding> findings = new ArrayList<>();
		for (OpenAiVisionClient.DamageFindingResult result : damageAnalysis.findings()) {
			findings.add(new VehicleDamageFinding(
					result.category(),
					result.severity(),
					result.summary(),
					result.panel(),
					result.recommendedAction()));
		}
		return findings;
	}

	private byte[] requireImage(MultipartFile image, String fieldName) {
		if (image == null || image.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must contain an image");
		}
		try {
			return image.getBytes();
		} catch (IOException exception) {
			throw new OpenAiIntegrationException("Failed to read uploaded image", exception);
		}
	}

	private String normalizeOdometerReading(String odometerReading) {
		return odometerReading == null ? "" : odometerReading.trim();
	}
}