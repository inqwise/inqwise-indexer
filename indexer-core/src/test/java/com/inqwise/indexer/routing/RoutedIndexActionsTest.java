package com.inqwise.indexer.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItems;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class RoutedIndexActionsTest {
	@Test
	void rejectsNonPositiveIndexerId() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> routedBuilder()
				.withIndexerId(0)
				.build()
		);

		assertEquals("indexerId must be positive", error.getMessage());
	}

	@Test
	void rejectsNonPositiveTargetId() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> routedBuilder()
				.withTargetId(-1)
				.build()
		);

		assertEquals("targetId must be positive", error.getMessage());
	}

	@Test
	void rejectsNegativeIndexerVersion() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> routedBuilder()
				.withIndexerVersion(-1L)
				.build()
		);

		assertEquals("indexerVersion must not be negative", error.getMessage());
	}

	@Test
	void rejectsBlankQueueName() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> routedBuilder()
				.withQueueName(" ")
				.build()
		);

		assertEquals("queueName must not be blank", error.getMessage());
	}

	@Test
	void rejectsEmptyActions() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> routedBuilder()
				.withActions(List.of())
				.build()
		);

		assertEquals("actions must not be empty", error.getMessage());
	}

	@Test
	void rejectsMissingActions() {
		NullPointerException error = assertThrows(
			NullPointerException.class,
			() -> routedBuilder()
				.withActions(null)
				.build()
		);

		assertEquals("actions", error.getMessage());
	}

	private RoutedIndexActions.Builder routedBuilder() {
		return RoutedIndexActions.builder()
			.withIndexerId(20)
			.withTargetId(10)
			.withIndexerVersion(0L)
			.withQueueName("queue-customers")
			.withActions(List.of(IndexerActionItems.concretePutDocument(
				10,
				20,
				"customers-2026-06",
				"42",
				new JsonObject()
			)));
	}
}
