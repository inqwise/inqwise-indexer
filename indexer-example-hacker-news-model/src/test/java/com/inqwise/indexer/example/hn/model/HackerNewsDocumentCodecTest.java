package com.inqwise.indexer.example.hn.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class HackerNewsDocumentCodecTest {
	private final HackerNewsDocumentCodec codec = new HackerNewsDocumentCodec();

	@Test
	void roundTripsTheConsumerDocument() {
		HackerNewsDocument document = HackerNewsDocument.builder()
			.withId(42)
			.withType("story")
			.withAuthor("ada")
			.withTime(1_700_000_000L)
			.withTitle("A useful tool")
			.withUrl("https://example.test/tool")
			.withScore(17)
			.withDescendants(3)
			.withKids(List.of(43L, 44L))
			.build();

		assertEquals(document, codec.decode(codec.encode(document)));
	}

	@Test
	void acceptsAdapterEnrichmentWithoutAddingItToTheModel() {
		JsonObject json = validStory().put("store_routing_key", "partition-1");

		assertEquals(42, codec.decode(json).id());
	}

	@Test
	void rejectsMissingAndMalformedRequiredValues() {
		assertThrows(
			IllegalArgumentException.class,
			() -> codec.decode(without("time"))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> codec.decode(validStory().put("score", "high"))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> codec.decode(validStory().put("kids", new JsonArray().add(-1)))
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> codec.decode(validStory().put("type", "unknown"))
		);
	}

	@Test
	void rejectsStoryWithoutTitle() {
		assertThrows(
			IllegalArgumentException.class,
			() -> codec.decode(without("title"))
		);
	}

	private JsonObject without(String field) {
		JsonObject json = validStory();
		json.remove(field);
		return json;
	}

	private JsonObject validStory() {
		return new JsonObject()
			.put("id", 42L)
			.put("type", "story")
			.put("by", "ada")
			.put("time", 1_700_000_000L)
			.put("title", "A useful tool")
			.put("score", 17)
			.put("descendants", 3)
			.put("kids", new JsonArray())
			.put("parts", new JsonArray())
			.put("source", HackerNewsDocumentConstants.SOURCE_NAME);
	}
}
