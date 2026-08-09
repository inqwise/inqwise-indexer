package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.query.ReportResultCodec;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class HackerNewsStoriesResultCodec
	implements ReportResultCodec<HackerNewsStoriesResult> {

	@Override
	public HackerNewsStoriesResult decode(JsonObject payload) {
		Objects.requireNonNull(payload, "payload");
		JsonArray stories = payload.getJsonArray("stories", new JsonArray());
		return HackerNewsStoriesResult.builder()
			.withStories(stories.stream()
				.map(JsonObject.class::cast)
				.map(this::decodeStory)
				.toList())
			.withNextCursor(payload.getString("next_cursor"))
			.build();
	}

	@Override
	public JsonObject encode(HackerNewsStoriesResult result) {
		Objects.requireNonNull(result, "result");
		JsonObject payload = new JsonObject().put(
			"stories",
			new JsonArray(result.stories().stream()
			.map(this::encodeStory)
			.toList())
		);
		if (result.nextCursor() != null) {
			payload.put("next_cursor", result.nextCursor());
		}
		return payload;
	}

	private HackerNewsStorySummary decodeStory(JsonObject json) {
		return HackerNewsStorySummary.builder()
			.withId(requireLong(json, "id"))
			.withAuthor(json.getString("author"))
			.withTitle(requireString(json, "title"))
			.withUrl(json.getString("url"))
			.withTime(Instant.parse(requireString(json, "time")))
			.withScore(requireInteger(json, "score"))
			.withDescendants(requireInteger(json, "descendants"))
			.build();
	}

	private JsonObject encodeStory(HackerNewsStorySummary story) {
		return new JsonObject()
			.put("id", story.id())
			.put("author", story.author())
			.put("title", story.title())
			.put("url", story.url())
			.put("time", story.time().toString())
			.put("score", story.score())
			.put("descendants", story.descendants());
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
