package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

abstract class IndexerLoadRepositoryCompletionContract {
	abstract IndexerLoadRepository createRepository();

	@Test
	void finalizationReplacesCancelledLoadWithCompletionTombstone(
		VertxTestContext testContext
	) {
		IndexerLoadRepository loads = createRepository();

		loads.insert(load(20, 10, null, IndexerLoadState.CANCELLED))
			.compose(ignored -> loads.finalizeCleanup(20, 0L))
			.compose(ignored -> assertCompleted(loads, 20, 10, IndexerLoadState.CANCELLED, 0L))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void finalizationReplacesPublishedLoadWithCompletionTombstone(
		VertxTestContext testContext
	) {
		IndexerLoadRepository loads = createRepository();

		loads.insert(load(20, 10, 30, IndexerLoadState.PUBLISHED))
			.compose(ignored -> loads.finalizeCleanup(20, 0L))
			.compose(ignored -> assertCompleted(loads, 20, 10, IndexerLoadState.PUBLISHED, 0L))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void finalizationRejectsStaleVersionWithoutDeletingLoadOrWritingCompletion(
		VertxTestContext testContext
	) {
		IndexerLoadRepository loads = createRepository();

		loads.insert(load(20, 10, null, IndexerLoadState.CANCELLED))
			.compose(ignored -> loads.finalizeCleanup(20, 1L).transform(result -> {
				assertTrue(result.failed());
				assertEquals(
					"Indexer load version conflict for id 20: expected 1 but was 0",
					result.cause().getMessage()
				);
				return assertLoadStillPresent(loads, 20);
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void finalizationRejectsNonTerminalLoadWithoutDeletingLoadOrWritingCompletion(
		VertxTestContext testContext
	) {
		IndexerLoadRepository loads = createRepository();

		loads.insert(load(20, 10, null, IndexerLoadState.HISTORICAL_LOADING))
			.compose(ignored -> loads.finalizeCleanup(20, 0L).transform(result -> {
				assertTrue(result.failed());
				assertEquals(
					"Indexer load is not cleanup-ready: HISTORICAL_LOADING",
					result.cause().getMessage()
				);
				return assertLoadStillPresent(loads, 20);
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void retainedCompletionPreventsReusingTheSameLoadIndexerId(
		VertxTestContext testContext
	) {
		IndexerLoadRepository loads = createRepository();

		loads.insert(load(20, 10, null, IndexerLoadState.CANCELLED))
			.compose(ignored -> loads.finalizeCleanup(20, 0L))
			.compose(ignored -> loads.insert(load(20, 11, null, IndexerLoadState.CREATED)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("Indexer load was already completed: 20", error.getMessage());
				testContext.completeNow();
			})));
	}

	@Test
	void finalizedLoadDoesNotBlockNewActiveLoadForSameTarget(
		VertxTestContext testContext
	) {
		IndexerLoadRepository loads = createRepository();

		loads.insert(load(20, 10, null, IndexerLoadState.CANCELLED))
			.compose(ignored -> loads.finalizeCleanup(20, 0L))
			.compose(ignored -> loads.insert(load(21, 10, null, IndexerLoadState.CREATED)))
			.compose(ignored -> loads.getActiveByTargetId(10))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(21, found.orElseThrow().indexerId());
				testContext.completeNow();
			})));
	}

	private Future<Void> assertCompleted(
		IndexerLoadRepository loads,
		Integer indexerId,
		Integer targetId,
		IndexerLoadState terminalState,
		long terminalVersion
	) {
		return Future.all(
			loads.getByIndexerId(indexerId),
			loads.getCompletionByIndexerId(indexerId),
			loads.getActiveByTargetId(targetId),
			loads.getActiveByTargetIndexerId(indexerId)
		).map(results -> {
			assertTrue(results.<java.util.Optional<IndexerLoadRecord>>resultAt(0).isEmpty());
			IndexerLoadCompletion completion =
				results.<java.util.Optional<IndexerLoadCompletion>>resultAt(1).orElseThrow();
			assertEquals(indexerId, completion.indexerId());
			assertEquals(terminalState, completion.terminalState());
			assertEquals(terminalVersion, completion.terminalVersion());
			assertNotNull(completion.completedAt());
			assertTrue(results.<java.util.Optional<IndexerLoadRecord>>resultAt(2).isEmpty());
			assertTrue(results.<java.util.Optional<IndexerLoadRecord>>resultAt(3).isEmpty());
			return null;
		});
	}

	private Future<Void> assertLoadStillPresent(
		IndexerLoadRepository loads,
		Integer indexerId
	) {
		return Future.all(
			loads.getByIndexerId(indexerId),
			loads.getCompletionByIndexerId(indexerId)
		).map(results -> {
			assertTrue(results.<java.util.Optional<IndexerLoadRecord>>resultAt(0).isPresent());
			assertTrue(results.<java.util.Optional<IndexerLoadCompletion>>resultAt(1).isEmpty());
			return null;
		});
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
