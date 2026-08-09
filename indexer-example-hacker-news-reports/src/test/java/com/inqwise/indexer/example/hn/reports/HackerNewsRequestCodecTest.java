package com.inqwise.indexer.example.hn.reports;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class HackerNewsRequestCodecTest {
	private static final JsonObject VALID_RANGE = new JsonObject()
		.put("from_inclusive", "2026-01-01T00:00:00Z")
		.put("to_exclusive", "2026-01-02T00:00:00Z");

	@Test
	void rejectsWrongParameterTypes() {
		HackerNewsStoriesRequestCodec stories = new HackerNewsStoriesRequestCodec();
		HackerNewsAuthorSummaryRequestCodec authors =
			new HackerNewsAuthorSummaryRequestCodec();

		assertThrows(
			IllegalArgumentException.class,
			() -> stories.decode(VALID_RANGE.copy().put("limit", "25"))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> authors.decode(VALID_RANGE.copy().put("order_by", 42))
		);
	}

	@Test
	void rejectsFractionalAndOutOfRangeIntegers() {
		HackerNewsStoriesRequestCodec stories = new HackerNewsStoriesRequestCodec();

		assertThrows(
			IllegalArgumentException.class,
			() -> stories.decode(VALID_RANGE.copy().put("minimum_score", 1.5))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> stories.decode(VALID_RANGE.copy().put("limit", 4_294_967_297L))
		);
	}
}
