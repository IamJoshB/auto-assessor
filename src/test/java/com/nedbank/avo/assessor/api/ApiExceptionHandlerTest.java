package com.nedbank.avo.assessor.api;

import com.nedbank.avo.assessor.exception.AssessmentNotFoundException;
import com.nedbank.avo.assessor.exception.AssessmentQuoteNotFoundException;
import com.nedbank.avo.assessor.exception.AssessmentReportNotFoundException;
import com.nedbank.avo.assessor.exception.DuplicateVehicleAngleException;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void handlesBadRequestExceptionsAs400() {
		ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
			handler.handleBadRequest(new IllegalArgumentException("bad request"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("bad request");
	}

	@Test
	void handlesNotFoundExceptionsAs404() {
		ResponseEntity<ApiExceptionHandler.ErrorResponse> assessmentResponse =
			handler.handleNotFound(new AssessmentNotFoundException("a-1"));
		ResponseEntity<ApiExceptionHandler.ErrorResponse> reportResponse =
			handler.handleReportNotFound(new AssessmentReportNotFoundException("a-1"));
		ResponseEntity<ApiExceptionHandler.ErrorResponse> quoteResponse =
			handler.handleQuoteNotFound(new AssessmentQuoteNotFoundException("a-1"));

		assertThat(assessmentResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(reportResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(quoteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void handlesConflictAs409() {
		ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
			handler.handleConflict(new DuplicateVehicleAngleException("a-1", VehicleImageAngle.FRONT_VIEW));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().error()).isEqualTo(HttpStatus.CONFLICT.getReasonPhrase());
	}

	@Test
	void handlesOpenAiFailuresAs502() {
		ResponseEntity<ApiExceptionHandler.ErrorResponse> response =
			handler.handleOpenAiFailure(new OpenAiIntegrationException("gateway error"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("gateway error");
	}
}
