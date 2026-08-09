package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.query.ReportResultCodec;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class HackerNewsAuthorSummaryResultCodec
	implements ReportResultCodec<HackerNewsAuthorSummaryResult> {

	@Override
	public HackerNewsAuthorSummaryResult decode(JsonObject payload) {
		Objects.requireNonNull(payload, "payload");
		JsonArray authors = payload.getJsonArray("authors", new JsonArray());
		return HackerNewsAuthorSummaryResult.builder()
			.withAuthors(authors.stream()
				.map(JsonObject.class::cast)
				.map(this::decodeAuthor)
				.toList())
			.build();
	}

	@Override
	public JsonObject encode(HackerNewsAuthorSummaryResult result) {
		Objects.requireNonNull(result, "result");
		return new JsonObject().put("authors", new JsonArray(result.authors().stream()
			.map(this::encodeAuthor)
			.toList()));
	}

	private HackerNewsAuthorSummary decodeAuthor(JsonObject json) {
		return HackerNewsAuthorSummary.builder()
			.withAuthor(requireString(json, "author"))
			.withStoryCount(requireLong(json, "story_count"))
			.withTotalScore(requireLong(json, "total_score"))
			.withMaxScore(requireInteger(json, "max_score"))
			.withLatestStoryTime(Instant.parse(requireString(json, "latest_story_time")))
			.build();
	}

	private JsonObject encodeAuthor(HackerNewsAuthorSummary author) {
		return new JsonObject()
			.put("author", author.author())
			.put("story_count", author.storyCount())
			.put("total_score", author.totalScore())
			.put("max_score", author.maxScore())
			.put("latest_story_time", author.latestStoryTime().toString());
	}

	private String requireString(JsonObject json, String field) {
		String value = json.getString(field);
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private long requireLong(JsonObject json, String field) {
		Long value = json.getLong(field);
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private int requireInteger(JsonObject json, String field) {
		Integer value = json.getInteger(field);
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}
}
