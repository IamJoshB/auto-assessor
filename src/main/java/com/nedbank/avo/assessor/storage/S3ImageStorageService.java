package com.nedbank.avo.assessor.storage;

import com.nedbank.avo.assessor.config.StorageProperties;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class S3ImageStorageService {

	private final S3Client s3Client;
	private final String bucketName;
	private final String s3Region;

	@Autowired
	public S3ImageStorageService(StorageProperties storageProperties, S3Client s3Client) {
		this.s3Client = s3Client;
		this.bucketName = storageProperties.s3Bucket();
		this.s3Region = storageProperties.s3Region();
	}

	public StoredImage storeOriginal(String assessmentId, VehicleImageAngle angle, String contentType, byte[] imageBytes) {
		return store(assessmentId, angle, "original", contentType, imageBytes);
	}

	public StoredImage storeLicenseDisc(String assessmentId, String contentType, byte[] imageBytes) {
		return store(assessmentId, "license_disc", "original", contentType, imageBytes);
	}

	public StoredImage storeVehicleImage(String assessmentId, VehicleImageAngle angle, String contentType, byte[] imageBytes) {
		return store(assessmentId, angle, "vehicle_image", contentType, imageBytes);
	}

	public StoredImage storeAssessmentReportHtml(String assessmentId, String htmlContent) {
		return storeReportFile(assessmentId, "vehicle-assessment-report.html", "text/html", htmlContent.getBytes(StandardCharsets.UTF_8));
	}

	public StoredImage storeAssessmentReportPdf(String assessmentId, byte[] pdfBytes) {
		return storeReportFile(assessmentId, "vehicle-assessment-report.pdf", "application/pdf", pdfBytes);
	}

	public StoredImage storeAssessmentQuoteHtml(String assessmentId, String htmlContent) {
		return storeQuoteFile(assessmentId, "vehicle-replacement-quote.html", "text/html", htmlContent.getBytes(StandardCharsets.UTF_8));
	}

	public StoredImage storeAssessmentQuotePdf(String assessmentId, byte[] pdfBytes) {
		return storeQuoteFile(assessmentId, "vehicle-replacement-quote.pdf", "application/pdf", pdfBytes);
	}

	public StoredImage storeQuoteProductImage(String assessmentId, String panelKey, String contentType, byte[] imageBytes) {
		String extension = extensionForContentType(contentType);
		String key = assessmentId + "/quote/products/" + sanitizePathSegment(panelKey) + "/" + UUID.randomUUID() + "." + extension;

		try {
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.contentType(normalizeContentType(contentType))
				.build();

			s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
		} catch (S3Exception exception) {
			throw new OpenAiIntegrationException("Failed to store quote product image to S3", exception);
		}

		return new StoredImage(buildPublicUrl(key), normalizeContentType(contentType));
	}

	public byte[] loadFile(String url) {
		try {
			String prefix = "https://" + bucketName + ".s3." + s3Region + ".amazonaws.com/";
			String key = url.startsWith(prefix) ? url.substring(prefix.length()) : url;
			GetObjectRequest getObjectRequest = GetObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.build();

			return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
		} catch (S3Exception exception) {
			throw new OpenAiIntegrationException("Failed to load file from S3", exception);
		}
	}

	private StoredImage store(String assessmentId, VehicleImageAngle angle, String type, String contentType, byte[] imageBytes) {
		return store(assessmentId, angle.name().toLowerCase(), type, contentType, imageBytes);
	}

	private StoredImage store(String assessmentId, String category, String type, String contentType, byte[] imageBytes) {
		String extension = extensionForContentType(contentType);
		String key = assessmentId + "/" + category + "/" + type + "/" + UUID.randomUUID() + "." + extension;

		try {
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.contentType(normalizeContentType(contentType))
				.build();

			s3Client.putObject(putObjectRequest, RequestBody.fromBytes(imageBytes));
		} catch (S3Exception exception) {
			throw new OpenAiIntegrationException("Failed to store image to S3", exception);
		}

		return new StoredImage(buildPublicUrl(key), normalizeContentType(contentType));
	}

	private StoredImage storeReportFile(String assessmentId, String fileName, String contentType, byte[] fileBytes) {
		String key = assessmentId + "/reports/" + fileName;

		try {
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.contentType(normalizeContentType(contentType))
				.build();

			s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
		} catch (S3Exception exception) {
			throw new OpenAiIntegrationException("Failed to store report file to S3", exception);
		}

		return new StoredImage(buildPublicUrl(key), normalizeContentType(contentType));
	}

	private StoredImage storeQuoteFile(String assessmentId, String fileName, String contentType, byte[] fileBytes) {
		String key = assessmentId + "/quotes/" + fileName;

		try {
			PutObjectRequest putObjectRequest = PutObjectRequest.builder()
				.bucket(bucketName)
				.key(key)
				.contentType(normalizeContentType(contentType))
				.build();

			s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));
		} catch (S3Exception exception) {
			throw new OpenAiIntegrationException("Failed to store quote file to S3", exception);
		}

		return new StoredImage(buildPublicUrl(key), normalizeContentType(contentType));
	}

	private String buildPublicUrl(String key) {
		return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, s3Region, key);
	}

	private String extensionForContentType(String contentType) {
		return switch (contentType) {
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			case "application/pdf" -> "pdf";
			case "text/html" -> "html";
			default -> "bin";
		};
	}

	private String normalizeContentType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return "application/octet-stream";
		}
		return contentType;
	}

	private String sanitizePathSegment(String segment) {
		if (segment == null) {
			return "";
		}
		// Remove or replace characters that could cause issues in S3 keys
		return segment.replaceAll("[^a-zA-Z0-9._-]", "_");
	}
}
