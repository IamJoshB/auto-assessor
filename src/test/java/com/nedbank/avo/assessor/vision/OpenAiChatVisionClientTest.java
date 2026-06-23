package com.nedbank.avo.assessor.vision;

import com.nedbank.avo.assessor.config.OpenAiProperties;
import com.nedbank.avo.assessor.domain.VehicleImageAngle;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiChatVisionClientTest {

	@Test
	void analyzeVehicleDamageThrowsWhenIntegrationDisabled() {
		OpenAiChatVisionClient client = new OpenAiChatVisionClient(
			WebClient.builder(),
			new ObjectMapper(),
			new OpenAiProperties("dummy", "https://api.openai.com", "gpt-4.1-mini", "gpt-image-1", null, false),
			new VehicleImageReader());

		assertThatThrownBy(() -> client.analyzeVehicleDamage(
			VehicleImageAngle.ODOMETER,
			null,
			"image/png",
			pngBytes()))
			.isInstanceOf(OpenAiIntegrationException.class)
			.hasMessageContaining("OpenAI integration is disabled");
	}

	@Test
	void extractVehicleDetailsThrowsWhenApiKeyMissing() {
		OpenAiChatVisionClient client = new OpenAiChatVisionClient(
			WebClient.builder(),
			new ObjectMapper(),
			new OpenAiProperties(" ", "https://api.openai.com", "gpt-4.1-mini", "gpt-image-1", null, true),
			new VehicleImageReader());

		assertThatThrownBy(() -> client.extractVehicleDetails("image/jpeg", new byte[] {1, 2, 3}))
			.isInstanceOf(OpenAiIntegrationException.class)
			.hasMessageContaining("OpenAI API key is not configured");
	}

	private byte[] pngBytes() {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", outputStream);
			return outputStream.toByteArray();
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to create PNG test image", exception);
		}
	}
}
