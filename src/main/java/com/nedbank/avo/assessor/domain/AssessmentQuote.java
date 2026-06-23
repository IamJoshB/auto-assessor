package com.nedbank.avo.assessor.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document("quotes")
@Getter
@Setter
@NoArgsConstructor
public class AssessmentQuote {

	@Id
	private String id;
	private String assessmentId;
	private VehicleDetails vehicleDetails;
	private String currency = "ZAR";
	private List<QuoteLineItem> lineItems = new ArrayList<>();
	private double subtotalZar;
	private double vatAmountZar;
	private double totalZar;
	private long totalTokensUsed;
	private String sourceModel;
	private String notes;
	private String quoteHtmlUrl;
	private String quotePdfUrl;
	private Instant createdAt;
	private Instant updatedAt;

	public AssessmentQuote(String assessmentId, VehicleDetails vehicleDetails, Instant createdAt, Instant updatedAt) {
		this.assessmentId = assessmentId;
		this.vehicleDetails = vehicleDetails;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}
