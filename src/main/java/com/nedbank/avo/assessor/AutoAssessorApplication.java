package com.nedbank.avo.assessor;

import com.nedbank.avo.assessor.config.OpenAiProperties;
import com.nedbank.avo.assessor.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties({OpenAiProperties.class, StorageProperties.class})
public class AutoAssessorApplication {
	public static void main(String[] args) {
		SpringApplication.run(AutoAssessorApplication.class, args);
	}
}
