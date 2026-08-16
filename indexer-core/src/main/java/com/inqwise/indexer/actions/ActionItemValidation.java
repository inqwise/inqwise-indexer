package com.inqwise.indexer.actions;

import java.util.Objects;
import java.util.Set;

import io.vertx.core.json.JsonObject;

final class ActionItemValidation {
	private ActionItemValidation() {
	}

	static String requiredText(String value, String name) {
		String text = Objects.requireNonNull(value, name);
		if (text.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}

		return text;
	}

	static String optionalText(String value, String name) {
		if (value != null && value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}

		return value;
	}

	static Integer requiredPositive(Integer value, String name) {
		Integer number = Objects.requireNonNull(value, name);
		if (number <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}

		return number;
	}

	static Integer optionalPositive(Integer value, String name) {
		if (value != null && value <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}

		return value;
	}

	static void requireOnlyFields(JsonObject json, String... allowedFields) {
		Objects.requireNonNull(json, "json");
		Set<String> allowed = Set.of(allowedFields);
		for (String field : json.fieldNames()) {
			if (!allowed.contains(field)) {
				throw new IllegalArgumentException("Unknown action field: " + field);
			}
		}
	}
}
