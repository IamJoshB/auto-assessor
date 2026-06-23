package com.nedbank.avo.assessor.vision;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.nedbank.avo.assessor.exception.OpenAiIntegrationException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
final class VehicleImageReader {

	BufferedImage read(byte[] imageBytes) {
		BufferedImage image = decodeImage(imageBytes);
		return applyOrientation(image, readOrientation(imageBytes));
	}

	private BufferedImage decodeImage(byte[] imageBytes) {
		try {
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
			if (image == null) {
				throw new OpenAiIntegrationException("Vehicle image was not a supported image type");
			}
			return image;
		} catch (IOException exception) {
			throw new OpenAiIntegrationException("Failed to read vehicle image", exception);
		}
	}

	private int readOrientation(byte[] imageBytes) {
		try {
			Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(imageBytes));
			ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
			if (directory == null || !directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
				return 1;
			}
			return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
		} catch (ImageProcessingException | IOException | MetadataException | RuntimeException exception) {
			return 1;
		}
	}

	private BufferedImage applyOrientation(BufferedImage source, int orientation) {
		return switch (orientation) {
			case 3 -> rotate180(source);
			case 6 -> rotate90Clockwise(source);
			case 8 -> rotate90CounterClockwise(source);
			default -> source;
		};
	}

	private BufferedImage rotate180(BufferedImage source) {
		AffineTransform transform = new AffineTransform();
		transform.translate(source.getWidth(), source.getHeight());
		transform.rotate(Math.PI);
		return transform(source, transform, source.getWidth(), source.getHeight());
	}

	private BufferedImage rotate90Clockwise(BufferedImage source) {
		AffineTransform transform = new AffineTransform();
		transform.translate(source.getHeight(), 0);
		transform.rotate(Math.PI / 2);
		return transform(source, transform, source.getHeight(), source.getWidth());
	}

	private BufferedImage rotate90CounterClockwise(BufferedImage source) {
		AffineTransform transform = new AffineTransform();
		transform.translate(0, source.getWidth());
		transform.rotate(-Math.PI / 2);
		return transform(source, transform, source.getHeight(), source.getWidth());
	}

	private BufferedImage transform(BufferedImage source, AffineTransform transform, int targetWidth, int targetHeight) {
		BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = target.createGraphics();
		try {
			graphics.drawImage(source, transform, null);
		} finally {
			graphics.dispose();
		}
		return target;
	}
}
