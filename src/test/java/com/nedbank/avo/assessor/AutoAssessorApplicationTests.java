package com.nedbank.avo.assessor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
	"app.openai.enabled=false",
	"app.openai.api-key=dummy-test-key",
	"spring.data.mongodb.uri=mongodb://localhost:27017/auto_assessor_test"
})
class AutoAssessorApplicationTests {

	@Test
	void contextLoads() {
	}

}
