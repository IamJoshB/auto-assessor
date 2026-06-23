package com.nedbank.avo.assessor.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("assessments")
@Getter
@Setter
@NoArgsConstructor
public class Assessment {

	@Id
	private String id;
	private VehicleDetails vehicleDetails;
	private String odometerReading;
	private long totalTokensUsed;
	private String licenseDiscImageUrl;
	private String licenseDiscImageContentType;
	private String reportHtmlUrl;
	private String reportPdfUrl;
	private String quoteHtmlUrl;
	private String quotePdfUrl;
	private AssessmentStatus status = AssessmentStatus.CREATED;
	private String errorMessage;
	private Instant createdAt;
	private Instant updatedAt;
	private boolean archived = false;
	private List<AssessmentImage> images = new ArrayList<>();

	public Assessment(VehicleDetails vehicleDetails, Instant createdAt, Instant updatedAt) {
		this.vehicleDetails = vehicleDetails;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}