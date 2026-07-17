package com.inqwise.indexer.catalog.indexers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerOperationsTest {
	@Test
	void repeatedMarkDeletingReturnsCurrentRecordWithoutDuplicateEvent(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());
		IndexerOperations operations = new MetadataIndexerOperations(
			repository,
			new MetadataChangeNotifier(registry, eventBus)
		);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> repository.insertTarget(new InsertTarget("test", "customers", null)))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> operations.markDeleting(
				new MarkIndexerDeletingRequest(indexerId, 0L)
			).compose(first -> operations.markDeleting(
				new MarkIndexerDeletingRequest(indexerId, 999L)
			).map(second -> new Result(first.orElseThrow(), second.orElseThrow(), 0L))))
			.compose(result -> registry.listInvalidations(10)
				.map(entries -> new Result(
					result.first(),
					result.second(),
					entries.entries().get(0).version()
				)))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(MutationState.DELETING, result.second().mutationState());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, result.second().runtimeState());
				assertEquals(result.first().version(), result.second().version());
				assertEquals(1, events.size());
				assertEquals(2L, result.invalidationVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void missingIndexerReturnsEmpty(VertxTestContext testContext) {
		IndexerOperations operations = new MetadataIndexerOperations(
			new InMemoryDocumentStoreMetadataRepository(),
			TestMetadataChangeNotifiers.create(new InMemoryIndexerLifecycleEventBus())
		);

		operations.markDeleting(new MarkIndexerDeletingRequest(404, 0L))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isEmpty());
				testContext.completeNow();
			})));
	}

	private record Result(
		IndexerDeletionResult first,
		IndexerDeletionResult second,
		long invalidationVersion
	) {
	}
}
