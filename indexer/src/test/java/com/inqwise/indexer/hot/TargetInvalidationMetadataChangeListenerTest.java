package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.TargetMetadataChanged;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetInvalidationMetadataChangeListenerTest {
	@Test
	void targetEventInvalidatesOnlyLocalView(VertxTestContext testContext) {
		RecordingHotMetadataView view = new RecordingHotMetadataView();
		TargetInvalidationMetadataChangeListener listener =
			new TargetInvalidationMetadataChangeListener(
				new InMemoryIndexerLifecycleEventBus(),
				view
			);

		listener.invalidate(new TargetMetadataChanged(
				10,
				"customers",
				"2026-05",
				"target.changed",
				1L
			))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of(10), view.invalidatedTargetIds);
				testContext.completeNow();
			})));
	}

	@Test
	void indexerEventInvalidatesOnlyLocalView(
		VertxTestContext testContext
	) {
		RecordingHotMetadataView view = new RecordingHotMetadataView();
		TargetInvalidationMetadataChangeListener listener =
			new TargetInvalidationMetadataChangeListener(
				new InMemoryIndexerLifecycleEventBus(),
				view
			);

		listener.invalidate(new IndexerMetadataChanged(
				20,
				10,
				"indexer.changed",
				0L
			)).onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of(20), view.invalidatedIndexerIds);
				testContext.completeNow();
			})));
	}

	@Test
	void startIsIdempotent(VertxTestContext testContext) {
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingHotMetadataView view = new RecordingHotMetadataView();
		TargetInvalidationMetadataChangeListener listener =
			new TargetInvalidationMetadataChangeListener(
				eventBus,
				view
			);

		listener.start()
			.compose(ignored -> listener.start())
			.compose(ignored -> eventBus.publish(new TargetMetadataChanged(
				10,
				"customers",
				"2026-05",
				"target.changed",
				1L
			)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of(10), view.invalidatedTargetIds);
				testContext.completeNow();
			})));
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
