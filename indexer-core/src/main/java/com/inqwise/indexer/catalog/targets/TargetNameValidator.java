package com.inqwise.indexer.catalog.targets;

import java.util.regex.Pattern;

public class TargetNameValidator {
	public static final int MAX_TARGET_NAME_LENGTH = 128;
	private static final Pattern TARGET_NAME = Pattern.compile("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?");
	private static final Pattern GENERATED_RESOURCE_NAME =
		Pattern.compile("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?--(?:idx|queue)-[a-f0-9-]{36}");

	private TargetNameValidator() {
	}

	public static String requireTargetName(String targetName) {
		if (targetName == null) {
			throw new NullPointerException("targetName");
		}

		if (targetName.length() > MAX_TARGET_NAME_LENGTH) {
			throw new IllegalArgumentException("Target name is too long: " + targetName.length());
		}

		if (!TARGET_NAME.matcher(targetName).matches() || targetName.contains("--")) {
			throw new IllegalArgumentException("Target name is not canonical: " + targetName);
		}

		return targetName;
	}

	public static String requireGeneratedResourceName(String resourceName) {
		if (resourceName == null) {
			throw new NullPointerException("resourceName");
		}

		if (!GENERATED_RESOURCE_NAME.matcher(resourceName).matches()) {
			throw new IllegalArgumentException("Generated resource name is not canonical: " + resourceName);
		}

		return resourceName;
	}
}
