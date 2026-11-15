package com.inqwise.indexer.load.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

abstract class IndexerLoadRepositoryPublicationContract extends IndexerLoadRepositoryCompletionContract {
	@Test
	void preparationRetainsExactIntentWithoutAdvancingLoadVersion(VertxTestContext testContext) {
		IndexerLoadRepository loads = createRepository();
		LoadPublication publication = publication(30, 0);

		loads.insert(publishableLoad())
			.compose(ignored -> loads.preparePublication(publication))
			.compose(ignored -> loads.preparePublication(publication))
			.compose(ignored -> loads.getPublication(20))
			.compose(found -> {
				assertEquals(publication, found.orElseThrow());
				return loads.getByIndexerId(20);
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(0, found.orElseThrow().version());
				assertEquals(IndexerLoadState.HISTORICAL_COMPLETE, found.orElseThrow().state());
				testContext.completeNow();
			})));
	}

	@Test
	void preparationRejectsStaleVersionWithoutSavingIntent(VertxTestContext testContext) {
		IndexerLoadRepository loads = createRepository();
		loads.insert(publishableLoad())
			.compose(ignored -> loads.preparePublication(publication(30, 1)).transform(result -> {
				assertTrue(result.failed());
				return loads.getPublication(20);
			}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void preparationRejectsDifferentCleanupIdentity(VertxTestContext testContext) {
		IndexerLoadRepository loads = createRepository();
		LoadPublication original = publication(30, 0);
		loads.insert(publishableLoad())
			.compose(ignored -> loads.preparePublication(original))
			.compose(ignored -> loads.preparePublication(publication(31, 0)).transform(result -> {
				assertTrue(result.failed());
				return loads.getPublication(20);
			}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(original, found.orElseThrow());
				testContext.completeNow();
			})));
	}

	@Test
	void preparedPublicationFencesCancellationFailureAndWriterChanges(VertxTestContext testContext) {
		IndexerLoadRepository loads = createRepository();
		loads.insert(publishableLoad())
			.compose(ignored -> loads.preparePublication(publication(30, 0)))
			.compose(ignored -> {
				List<Future<Void>> mutations = List.of(
					loads.updateState(UpdateIndexerLoadState.builder()
						.withIndexerId(20).withExpectedVersion(0).withState(IndexerLoadState.CANCELLED).build()),
					loads.markFailed(UpdateIndexerLoadFailure.builder()
						.withIndexerId(20).withExpectedVersion(0).withFailureReason("late failure").build()),
					loads.attachLiveWriterIfAbsent(AttachLiveWriterRequest.builder()
						.withIndexerId(20).withExpectedVersion(0).withLiveIndexerId(21).build()).mapEmpty(),
					loads.approve(UpdateIndexerLoadApproval.builder()
						.withIndexerId(20).withExpectedVersion(0).build()),
					loads.requestBarrier(RequestIndexerLoadBarrier.builder()
						.withIndexerId(20).withExpectedVersion(0).withBarrierId("late").withBarrierTimestamp(java.time.Instant.now()).build()),
					loads.markBarrierReached(UpdateIndexerLoadBarrier.builder()
						.withIndexerId(20).withExpectedVersion(0).withBarrierId("late").withBarrierTimestamp(java.time.Instant.now()).withReachedAt(java.time.Instant.now()).build())
				);
				return Future.all(mutations.stream().map(mutation -> mutation.transform(result -> {
					assertTrue(result.failed(), "Only publication may advance a prepared load");
					assertEquals("Load publication is already prepared: 20", result.cause().getMessage());
					return Future.succeededFuture();
				})).toList());
			})
			.compose(ignored -> loads.getByIndexerId(20))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(0, found.orElseThrow().version());
				assertEquals(IndexerLoadState.HISTORICAL_COMPLETE, found.orElseThrow().state());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanupReplacesPreparedPublicationWithMinimalCompletion(VertxTestContext testContext) {
		IndexerLoadRepository loads = createRepository();
		loads.insert(publishableLoad())
			.compose(ignored -> loads.preparePublication(publication(30, 0)))
			.compose(ignored -> loads.updateState(UpdateIndexerLoadState.builder()
				.withIndexerId(20).withExpectedVersion(0).withState(IndexerLoadState.PUBLISHED).build()))
			.compose(ignored -> loads.getPublication(20))
			.compose(found -> {
				assertEquals(publication(30, 0), found.orElseThrow());
				return loads.finalizeCleanup(20, 1);
			})
			.compose(ignored -> loads.getPublication(20))
			.compose(found -> {
				assertTrue(found.isEmpty());
				return loads.getCompletionByIndexerId(20);
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerLoadState.PUBLISHED, found.orElseThrow().terminalState());
				assertEquals(1, found.orElseThrow().terminalVersion());
				testContext.completeNow();
			})));
	}

	private InsertIndexerLoad publishableLoad() {
		return InsertIndexerLoad.builder()
			.withIndexerId(20).withTargetId(10).withProviderId("default")
			.withLiveWriterPolicy(LiveWriterPolicy.NONE).withState(IndexerLoadState.HISTORICAL_COMPLETE).build();
	}

	private LoadPublication publication(int oldId, long expectedVersion) {
		LoadIndexerReference candidate = LoadIndexerReference.builder().withId(20).withTargetId(10).withVersion(0L).build();
		return LoadPublication.builder().withExpectedLoadVersion(expectedVersion)
			.withLoadWriter(candidate).withCandidate(candidate)
			.withOldPublished(LoadIndexerReference.builder().withId(oldId).withTargetId(10).withVersion(0L).build()).build();
	}
}
