package com.inqwise.indexer.example.hn.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class HackerNewsDocumentCodec {
	public HackerNewsDocument decode(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return HackerNewsDocument.builder()
			.withId(requireLong(json, "id"))
			.withType(requireString(json, "type"))
			.withAuthor(optionalString(json, "by"))
			.withTime(requireLong(json, "time"))
			.withTitle(optionalString(json, "title"))
			.withUrl(optionalString(json, "url"))
			.withText(optionalString(json, "text"))
			.withParent(optionalLong(json, "parent"))
			.withPoll(optionalLong(json, "poll"))
			.withScore(optionalInteger(json, "score", 0))
			.withDescendants(optionalInteger(json, "descendants", 0))
			.withKids(ids(json, "kids"))
			.withParts(ids(json, "parts"))
			.withSource(requireString(json, "source"))
			.build();
	}

	public JsonObject encode(HackerNewsDocument document) {
		Objects.requireNonNull(document, "document");
		return new JsonObject()
			.put("id", document.id())
			.put("type", document.type())
			.put("by", document.author())
			.put("time", document.time())
			.put("title", document.title())
			.put("url", document.url())
			.put("text", document.text())
			.put("parent", document.parent())
			.put("poll", document.poll())
			.put("score", document.score())
			.put("descendants", document.descendants())
			.put("kids", new JsonArray(document.kids()))
			.put("parts", new JsonArray(document.parts()))
			.put("source", document.source());
	}

	private String requireString(JsonObject json, String field) {
		String value = optionalString(json, field);
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private String optionalString(JsonObject json, String field) {
		Object value = json.getValue(field);
		if (value == null) {
			return null;
		}
		if (!(value instanceof String text)) {
			throw new IllegalArgumentException(field + " must be a string");
		}
		return text;
	}

	private long requireLong(JsonObject json, String field) {
		Long value = optionalLong(json, field);
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private Long optionalLong(JsonObject json, String field) {
		Object value = json.getValue(field);
		if (value == null) {
			return null;
		}
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException(field + " must be an integer");
		}
		try {
			return new BigDecimal(number.toString()).longValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(field + " must be an integer", error);
		}
	}

	private int optionalInteger(JsonObject json, String field, int defaultValue) {
		Long value = optionalLong(json, field);
		if (value == null) {
			return defaultValue;
		}
		try {
			return Math.toIntExact(value);
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(field + " is outside integer range", error);
		}
	}

	private List<Long> ids(JsonObject json, String field) {
		Object value = json.getValue(field);
		if (value == null) {
			return List.of();
		}
		if (!(value instanceof JsonArray array)) {
			throw new IllegalArgumentException(field + " must be an array");
		}
		return array.stream()
			.map(item -> id(item, field))
			.toList();
	}

	private long id(Object value, String field) {
		if (!(value instanceof Number number)) {
			throw new IllegalArgumentException(field + " must contain integer ids");
		}
		try {
			return new BigDecimal(number.toString()).longValueExact();
		} catch (ArithmeticException error) {
			throw new IllegalArgumentException(
				field + " must contain integer ids",
				error
			);
		}
	}
}
