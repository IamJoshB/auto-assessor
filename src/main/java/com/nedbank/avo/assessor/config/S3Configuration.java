package com.nedbank.avo.assessor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class S3Configuration {

	@Bean
	public S3Client s3Client(StorageProperties storageProperties) {
		Region region = Region.of(storageProperties.s3Region());
		
		AwsCredentialsProvider credentialsProvider;
		if (hasText(storageProperties.s3AccessKey()) && hasText(storageProperties.s3SecretKey())) {
			// Use explicit credentials if provided
			AwsBasicCredentials credentials = AwsBasicCredentials.create(
				storageProperties.s3AccessKey(),
				storageProperties.s3SecretKey()
			);
			credentialsProvider = () -> credentials;
		} else {
			// Use default credential chain (environment variables, IAM role, etc.)
			credentialsProvider = DefaultCredentialsProvider.create();
		}
		
		return S3Client.builder()
			.region(region)
			.credentialsProvider(credentialsProvider)
			.build();
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
