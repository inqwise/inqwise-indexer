package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
			LiveWriterPolicy.NONE,
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
			LiveWriterPolicy.NONE,
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

	@Test
	void attachLiveWriterIfAbsentAttachesAndIncrementsVersion() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		loads.insert(load(20, 10, null, IndexerLoadState.HISTORICAL_LOADING));

		var result = loads.attachLiveWriterIfAbsent(new AttachLiveWriterRequest(20, 30, 0L));

		assertTrue(result.succeeded());
		assertTrue(result.result().attached());
		assertEquals(30, result.result().liveIndexerId());
		assertEquals(1L, result.result().version());
		var found = loads.getByIndexerId(20).result().orElseThrow();
		assertEquals(30, found.liveIndexerId());
		assertEquals(1L, found.version());
	}

	@Test
	void attachLiveWriterIfAbsentIsIdempotentForSameLiveWriter() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		loads.insert(load(20, 10, 30, IndexerLoadState.HISTORICAL_LOADING));

		var result = loads.attachLiveWriterIfAbsent(new AttachLiveWriterRequest(20, 30, 0L));

		assertTrue(result.succeeded());
		assertTrue(result.result().attached());
		assertEquals(30, result.result().liveIndexerId());
		assertEquals(0L, result.result().version());
	}

	@Test
	void attachLiveWriterIfAbsentReturnsWinnerWhenAnotherLiveWriterExists() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		loads.insert(load(20, 10, 30, IndexerLoadState.HISTORICAL_LOADING));

		var result = loads.attachLiveWriterIfAbsent(new AttachLiveWriterRequest(20, 31, 99L));

		assertTrue(result.succeeded());
		assertFalse(result.result().attached());
		assertEquals(30, result.result().liveIndexerId());
		assertEquals(0L, result.result().version());
	}

	@Test
	void attachLiveWriterIfAbsentFailsOnVersionConflictWithoutWinner() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		loads.insert(load(20, 10, null, IndexerLoadState.HISTORICAL_LOADING));

		var result = loads.attachLiveWriterIfAbsent(new AttachLiveWriterRequest(20, 30, 1L));

		assertTrue(result.failed());
		assertEquals(
			"Indexer load version conflict for id 20: expected 1 but was 0",
			result.cause().getMessage()
		);
	}

	@Test
	void attachLiveWriterIfAbsentFailsForTerminalLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		loads.insert(load(20, 10, null, IndexerLoadState.CANCELLED));

		var result = loads.attachLiveWriterIfAbsent(new AttachLiveWriterRequest(20, 30, 0L));

		assertTrue(result.failed());
		assertEquals("Indexer load is not active: CANCELLED", result.cause().getMessage());
	}

	private InsertIndexerLoad load(
		Integer indexerId,
		Integer targetId,
		Integer liveIndexerId,
		IndexerLoadState state
	) {
		return new InsertIndexerLoad(
			indexerId,
			targetId,
			liveIndexerId,
			LiveWriterPolicy.NONE,
			"default",
			state,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		);
	}
}
