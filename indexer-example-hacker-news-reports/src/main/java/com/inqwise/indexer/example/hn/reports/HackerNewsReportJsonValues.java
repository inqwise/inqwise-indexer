package com.inqwise.indexer.example.hn.reports;

import java.math.BigDecimal;

import io.vertx.core.json.JsonObject;

final class HackerNewsReportJsonValues {
	private HackerNewsReportJsonValues() {
	}

	static String requiredString(JsonObject json, String field) {
		String value = optionalString(json, field, null);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	static String optionalString(JsonObject json, String field, String defaultValue) {
		Object value = json.getValue(field);
		if (value == null) {
			return defaultValue;
		}
		if (!(value instanceof String text)) {
			throw new IllegalArgumentException(field + " must be a string");
		}
		return text;
	}

	static int optionalInteger(JsonObject json, String field, int defaultValue) {
		Object value = json.getValue(field);
		if (value == null) {
			return defaultValue;
		}
		long exact = exactLong(value, field);
		try {
			return Math.toIntExact(exact);
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(field + " is outside integer range", error);
		}
	}

	static long requiredLong(JsonObject json, String field) {
		Object value = json.getValue(field);
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return exactLong(value, field);
	}

	private static long exactLong(Object value, String field) {
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException(field + " must be an integer");
		}
		try {
			return new BigDecimal(number.toString()).longValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(field + " must be an integer", error);
		}
	}
}
