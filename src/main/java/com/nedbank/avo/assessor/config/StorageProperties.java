package com.nedbank.avo.assessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
	String s3Bucket,
	String s3Region,
	String s3AccessKey,
	String s3SecretKey
) {

	public StorageProperties {
		s3Bucket = hasText(s3Bucket) ? s3Bucket : "";
		s3Region = hasText(s3Region) ? s3Region : "us-east-1";
		s3AccessKey = hasText(s3AccessKey) ? s3AccessKey : "";
		s3SecretKey = hasText(s3SecretKey) ? s3SecretKey : "";
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}