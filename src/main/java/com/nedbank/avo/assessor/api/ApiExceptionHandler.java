package com.nedbank.avo.assessor.api;

import com.nedbank.avo.assessor.exception.AssessmentNotFoundException;
import com.nedbank.avo.assessor.exception.AssessmentQuoteNotFoundException;
import com.nedbank.avo.assessor.exception.AssessmentReportNotFoundException;
import com.nedbank.avo.assessor.exception.DuplicateVehicleAngleException;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler({
		ConstraintViolationException.class,
		MethodArgumentNotValidException.class,
		IllegalArgumentException.class
	})
	public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
		return build(HttpStatus.BAD_REQUEST, exception.getMessage());
	}

	@ExceptionHandler(AssessmentNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(AssessmentNotFoundException exception) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(AssessmentReportNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleReportNotFound(AssessmentReportNotFoundException exception) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(AssessmentQuoteNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleQuoteNotFound(AssessmentQuoteNotFoundException exception) {
		return build(HttpStatus.NOT_FOUND, exception.getMessage());
	}

	@ExceptionHandler(DuplicateVehicleAngleException.class)
	public ResponseEntity<ErrorResponse> handleConflict(DuplicateVehicleAngleException exception) {
		return build(HttpStatus.CONFLICT, exception.getMessage());
	}

	@ExceptionHandler(OpenAiIntegrationException.class)
	public ResponseEntity<ErrorResponse> handleOpenAiFailure(OpenAiIntegrationException exception) {
		return build(HttpStatus.BAD_GATEWAY, exception.getMessage());
	}

	private ResponseEntity<ErrorResponse> build(HttpStatus status, String message) {
		return ResponseEntity.status(status)
			.body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message));
	}

	public record ErrorResponse(Instant timestamp, int status, String error, String message) {
	}
}