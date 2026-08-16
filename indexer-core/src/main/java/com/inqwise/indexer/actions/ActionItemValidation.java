package com.inqwise.indexer.actions;

import java.util.Objects;

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
}
