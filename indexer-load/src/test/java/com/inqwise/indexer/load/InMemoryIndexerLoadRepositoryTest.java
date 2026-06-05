package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class InMemoryIndexerLoadRepositoryTest {
	@Test
	void rejectsSecondActiveLoadForSameTarget() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();

		var first = loads.insert(new InsertIndexerLoad(
			20,
			10,
			null,
			"default",
			IndexerLoadState.HISTORICAL_LOADING,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		));
		assertTrue(first.succeeded());

		var result = loads.insert(new InsertIndexerLoad(
			21,
			10,
			null,
			"default",
			IndexerLoadState.CREATED,
			Instant.parse("2026-06-05T11:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		));

		assertTrue(result.failed());
		assertEquals("Active indexer load already exists for target: 10", result.cause().getMessage());
	}
}
