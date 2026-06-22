package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetInvalidationMetadataChangeListenerTest {
	@Test
	void targetEventInvalidatesLocalViewAndMarksRegistry(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());
		RecordingHotMetadataView view = new RecordingHotMetadataView();
		TargetInvalidationMetadataChangeListener listener =
			new TargetInvalidationMetadataChangeListener(
				repository,
				new InMemoryIndexerLifecycleEventBus(),
				view,
				registry
			);

		listener.invalidate(new TargetMetadataChanged(10, "target.changed", 1L))
			.compose(ignored -> registry.listInvalidations(10))
			.onComplete(testContext.succeeding(entries -> testContext.verify(() -> {
				assertEquals(List.of(10), view.invalidatedTargetIds);
				assertEquals(10, entries.entries().get(0).concreteTargetId());
				testContext.completeNow();
			})));
	}

	@Test
	void indexerEventInvalidatesLocalViewAndMarksOwningTarget(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());
		RecordingHotMetadataView view = new RecordingHotMetadataView();
		TargetInvalidationMetadataChangeListener listener =
			new TargetInvalidationMetadataChangeListener(
				repository,
				new InMemoryIndexerLifecycleEventBus(),
				view,
				registry
			);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> listener.invalidate(new IndexerMetadataChanged(
				indexerId,
				"indexer.changed",
				0L
			)).map(indexerId)))
			.compose(indexerId -> registry.listInvalidations(10).map(entries ->
				new ListenerResult(indexerId, entries)))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(List.of(result.indexerId()), view.invalidatedIndexerIds);
				assertEquals(1, result.entries().entries().get(0).concreteTargetId());
				testContext.completeNow();
			})));
	}

	@Test
	void startIsIdempotent(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());
		TargetInvalidationMetadataChangeListener listener =
			new TargetInvalidationMetadataChangeListener(
				repository,
				eventBus,
				new RecordingHotMetadataView(),
				registry
			);

		listener.start()
			.compose(ignored -> listener.start())
			.compose(ignored -> eventBus.publish(new TargetMetadataChanged(
				10,
				"target.changed",
				1L
			)))
			.compose(ignored -> registry.listInvalidations(10))
			.onComplete(testContext.succeeding(entries -> testContext.verify(() -> {
				assertEquals(1L, entries.entries().get(0).version());
				testContext.completeNow();
			})));
	}

	private record ListenerResult(
		Integer indexerId,
		TargetInvalidationEntries entries
	) {
	}

	private static class RecordingHotMetadataView implements HotMetadataView {
		private final List<Integer> invalidatedTargetIds = new ArrayList<>();
		private final List<Integer> invalidatedIndexerIds = new ArrayList<>();

		@Override
		public Optional<HotTarget> findTargetByName(String targetName) {
			return Optional.empty();
		}

		@Override
		public Optional<HotIndexer> findIndexerById(Integer indexerId) {
			return Optional.empty();
		}

		@Override
		public Future<Void> refreshHotTargetByConcreteTargetId(Integer targetId) {
			return Future.succeededFuture();
		}

		@Override
		public void invalidateHotTargetByConcreteTargetId(Integer targetId) {
			invalidatedTargetIds.add(targetId);
		}

		@Override
		public void invalidateHotTargetByIndexerId(Integer indexerId) {
			invalidatedIndexerIds.add(indexerId);
		}

		@Override
		public void invalidateAllHotTargets() {
		}
	}
}
