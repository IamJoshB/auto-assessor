package com.nedbank.avo.assessor.repository;

import com.nedbank.avo.assessor.domain.Assessment;
import com.nedbank.avo.assessor.domain.AssessmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface AssessmentRepository extends MongoRepository<Assessment, String> {

	@Query("{ 'archived': false }")
	Page<Assessment> findAllActive(Pageable pageable);

	@Query("{ 'archived': false }")
	List<Assessment> findAllActiveList();

	@Query("{ 'archived': false, 'status': ?0 }")
	Page<Assessment> findByStatus(AssessmentStatus status, Pageable pageable);

	@Query("{ 'archived': false }")
	long countActive();

	@Query("{ 'archived': false, 'status': ?0 }")
	long countByStatus(AssessmentStatus status);
}
