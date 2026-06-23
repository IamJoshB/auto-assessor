package com.nedbank.avo.assessor.service;

import com.nedbank.avo.assessor.api.dto.AssessmentImageUploadResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentDetailsResponse;
import com.nedbank.avo.assessor.api.dto.AssessmentListItemResponse;
import com.nedbank.avo.assessor.api.dto.CreateAssessmentResponse;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface AssessmentService {

	CreateAssessmentResponse createAssessment(MultipartFile licenseDiscImage);

	AssessmentImageUploadResponse analyzeVehicleImage(String assessmentId, VehicleImageAngle angle, MultipartFile image);

	Page<AssessmentListItemResponse> getAssessments(Pageable pageable);

	AssessmentDetailsResponse getAssessmentById(String assessmentId);

	byte[] getAssessmentReportPdf(String assessmentId);

	byte[] getAssessmentQuotePdf(String assessmentId);
}