package com.nedbank.avo.assessor.repository;

import com.nedbank.avo.assessor.domain.AssessmentQuote;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AssessmentQuoteRepository extends MongoRepository<AssessmentQuote, String> {

	Optional<AssessmentQuote> findByAssessmentId(String assessmentId);
}
