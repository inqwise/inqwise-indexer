package com.inqwise.indexer.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

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
		IndexerOperations operations = new MetadataIndexerOperations(repository, eventBus);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> repository.insertTarget(new InsertTarget(null, "customers", null)))
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
			).map(second -> new Result(first.orElseThrow(), second.orElseThrow()))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(MutationState.DELETING, result.second().mutationState());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, result.second().runtimeState());
				assertEquals(result.first().version(), result.second().version());
				assertEquals(1, events.size());
				testContext.completeNow();
			})));
	}

	@Test
	void missingIndexerReturnsEmpty(VertxTestContext testContext) {
		IndexerOperations operations = new MetadataIndexerOperations(
			new InMemoryDocumentStoreMetadataRepository(),
			new InMemoryIndexerLifecycleEventBus()
		);

		operations.markDeleting(new MarkIndexerDeletingRequest(404, 0L))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isEmpty());
				testContext.completeNow();
			})));
	}

	private record Result(
		com.inqwise.indexer.metadata.IndexerRecord first,
		com.inqwise.indexer.metadata.IndexerRecord second
	) {
	}
}
