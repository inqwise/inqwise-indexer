package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.hot.TargetInvalidationEntries;
import com.inqwise.indexer.hot.TargetInvalidationRegistry;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MetadataChangeNotifierTest {
	@Test
	void marksRegistryBeforePublishingIndexerWakeUp(VertxTestContext testContext) {
		List<String> steps = new ArrayList<>();
		TargetInvalidationRegistry registry = recordingRegistry(steps);
		IndexerLifecycleEventBus eventBus = recordingEventBus(steps);
		MetadataChangeNotifier notifier = new MetadataChangeNotifier(registry, eventBus);

		notifier.indexerChanged(new IndexerMetadataChanged(20, 10, "changed", 1L))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of("mark:10", "indexer:20"), steps);
				testContext.completeNow();
			})));
	}

	@Test
	void registryFailureSuppressesWakeUpAndFailsWorkflow(VertxTestContext testContext) {
		List<String> steps = new ArrayList<>();
		TargetInvalidationRegistry registry = new TargetInvalidationRegistry() {
			@Override
			public Future<Void> markInvalidated(Integer concreteTargetId) {
				steps.add("mark:" + concreteTargetId);
				return Future.failedFuture("registry unavailable");
			}

			@Override
			public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
				return Future.succeededFuture(new TargetInvalidationEntries(List.of(), false));
			}
		};
		MetadataChangeNotifier notifier = new MetadataChangeNotifier(
			registry,
			recordingEventBus(steps)
		);

		notifier.indexerChanged(new IndexerMetadataChanged(20, 10, "changed", 1L))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("registry unavailable", error.getMessage());
				assertEquals(List.of("mark:10"), steps);
				testContext.completeNow();
			})));
	}

	@Test
	void confirmsRetryWithoutPublishingWakeUp(VertxTestContext testContext) {
		List<String> steps = new ArrayList<>();
		MetadataChangeNotifier notifier = new MetadataChangeNotifier(
			recordingRegistry(steps),
			recordingEventBus(steps)
		);

		notifier.confirmTargetInvalidated(10)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of("mark:10"), steps);
				testContext.completeNow();
			})));
	}

	private TargetInvalidationRegistry recordingRegistry(List<String> steps) {
		return new TargetInvalidationRegistry() {
			@Override
			public Future<Void> markInvalidated(Integer concreteTargetId) {
				steps.add("mark:" + concreteTargetId);
				return Future.succeededFuture();
			}

			@Override
			public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
				return Future.succeededFuture(new TargetInvalidationEntries(List.of(), false));
			}
		};
	}

	private IndexerLifecycleEventBus recordingEventBus(List<String> steps) {
		return new InMemoryIndexerLifecycleEventBus() {
			@Override
			public void publishIndexerWakeUp(IndexerMetadataChanged event) {
				steps.add("indexer:" + event.getIndexerId());
			}
		};
	}
}
