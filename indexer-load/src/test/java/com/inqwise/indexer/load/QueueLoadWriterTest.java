package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.InMemoryIndexerQueue;

class QueueLoadWriterTest {
	@Test
	void failMarksActiveLoadFailed() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		loads.insert(load(IndexerLoadState.HISTORICAL_LOADING));

		var failed = writer.fail(new IllegalStateException("source unavailable"));

		assertTrue(failed.succeeded());
		var found = loads.getByIndexerId(20).result().orElseThrow();
		assertEquals(IndexerLoadState.FAILED, found.state());
		assertEquals("source unavailable", found.failureReason());
	}

	@Test
	void failDoesNotOverwriteCancelledLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		loads.insert(load(IndexerLoadState.CANCELLED));

		var failed = writer.fail(new IllegalStateException("late provider failure"));

		assertTrue(failed.succeeded());
		var found = loads.getByIndexerId(20).result().orElseThrow();
		assertEquals(IndexerLoadState.CANCELLED, found.state());
	}

	@Test
	void failDoesNotOverwritePublishedLoad() {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		QueueLoadWriter writer = writer(loads);

		loads.insert(load(IndexerLoadState.PUBLISHED));

		var failed = writer.fail(new IllegalStateException("late provider failure"));

		assertTrue(failed.succeeded());
		var found = loads.getByIndexerId(20).result().orElseThrow();
		assertEquals(IndexerLoadState.PUBLISHED, found.state());
	}

	private QueueLoadWriter writer(InMemoryIndexerLoadRepository loads) {
		return new QueueLoadWriter(
			10,
			20,
			"customers--queue-load",
			new InMemoryIndexerQueue(),
			loads
		);
	}

	private InsertIndexerLoad load(IndexerLoadState state) {
		return new InsertIndexerLoad(
			20,
			10,
			null,
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
