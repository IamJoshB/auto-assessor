package com.nedbank.avo.assessor.service;

import java.util.Locale;

public final class VehiclePanelNormalizer {

	private VehiclePanelNormalizer() {
	}

	public static String normalizePanelKey(String panel) {
		String normalized = normalize(panel);
		if (normalized.isBlank()) {
			return "";
		}

		if (normalized.contains("front") && normalized.contains("bumper")) {
			return "front_bumper";
		}
		if (normalized.contains("rear") && normalized.contains("bumper")) {
			return "rear_bumper";
		}
		if (normalized.contains("bonnet") || normalized.contains("hood")) {
			return "bonnet";
		}
		if (normalized.contains("boot") || normalized.contains("trunk")) {
			return "boot";
		}
		if (normalized.contains("roof")) {
			return "roof";
		}
		if (normalized.contains("windscreen") || normalized.contains("windshield")) {
			return "windscreen";
		}
		if ((normalized.contains("front") || normalized.contains("fender")) && (normalized.contains("left") || normalized.contains("driver"))) {
			return "front_left_fender";
		}
		if ((normalized.contains("front") || normalized.contains("fender")) && (normalized.contains("right") || normalized.contains("passenger"))) {
			return "front_right_fender";
		}
		if (normalized.contains("rear") && (normalized.contains("quarter") || normalized.contains("arch")) && (normalized.contains("left") || normalized.contains("driver"))) {
			return "rear_left_quarter";
		}
		if (normalized.contains("rear") && (normalized.contains("quarter") || normalized.contains("arch")) && (normalized.contains("right") || normalized.contains("passenger"))) {
			return "rear_right_quarter";
		}
		if (normalized.contains("front") && normalized.contains("door") && (normalized.contains("left") || normalized.contains("driver"))) {
			return "front_left_door";
		}
		if (normalized.contains("front") && normalized.contains("door") && (normalized.contains("right") || normalized.contains("passenger"))) {
			return "front_right_door";
		}
		if (normalized.contains("rear") && normalized.contains("door") && (normalized.contains("left") || normalized.contains("driver"))) {
			return "rear_left_door";
		}
		if (normalized.contains("rear") && normalized.contains("door") && (normalized.contains("right") || normalized.contains("passenger"))) {
			return "rear_right_door";
		}
		if (normalized.contains("mirror") && (normalized.contains("left") || normalized.contains("driver"))) {
			return "left_side_mirror";
		}
		if (normalized.contains("mirror") && (normalized.contains("right") || normalized.contains("passenger"))) {
			return "right_side_mirror";
		}

		return normalized.replace(' ', '_');
	}

	public static String toDisplayLabel(String panelKey) {
		if (panelKey == null || panelKey.isBlank()) {
			return "Unknown Panel";
		}
		String[] tokens = panelKey.replace('-', '_').split("_");
		StringBuilder label = new StringBuilder();
		for (String token : tokens) {
			if (token.isBlank()) {
				continue;
			}
			if (label.length() > 0) {
				label.append(' ');
			}
			label.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
		}
		return label.toString();
	}

	private static String normalize(String value) {
		if (value == null) {
			return "";
		}
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
	}
}
