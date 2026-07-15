package com.inqwise.indexer.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MetadataDeleteIndexerCommandTest {
	@Test
	void metadataDeleteFinalizesIndexerAfterCleanup(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		List<IndexerMetadataChanged> events = new ArrayList<>();
		InMemoryCommandEngine commandService = commandService(repository, eventBus);

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(repository))
			.compose(indexerId -> commandService.submit(new DeleteIndexerCommand(indexerId, 0L))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isEmpty());
				assertEquals(1, events.size());
				assertEquals(DeleteIndexerCommand.TYPE, events.get(0).getCommandType());
				assertEquals(2L, events.get(0).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataDeleteRequiresExpectedVersionForExistingIndexer(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandEngine commandService = commandService(repository, eventBus);

		insertIndexer(repository)
			.compose(indexerId -> commandService.submit(new DeleteIndexerCommand(indexerId)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Expected version is required"));
				testContext.completeNow();
			})));
	}

	@Test
	void metadataDeleteTreatsMissingIndexerAsCleanupSuccess(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		List<IndexerMetadataChanged> events = new ArrayList<>();
		InMemoryCommandEngine commandService = commandService(repository, eventBus);

		eventBus.subscribe(events::add)
			.compose(ignored -> commandService.submit(new DeleteIndexerCommand(404, 0L)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(events.isEmpty());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		InMemoryCommandEngine commandService = new InMemoryCommandEngine();
		return commandService
			.register(new CleanupDeletingIndexerCommandHandler(
				repository,
				com.inqwise.indexer.provisioning.IndexerQueueResourceManager.NOOP,
				IndexerDocumentIndexResourceManager.NOOP
			))
			.register(new DeleteIndexerCommandHandler(
				new MetadataIndexerOperations(
					repository,
					TestMetadataChangeNotifiers.create(eventBus)
				),
				commandService
			));
	}

	private Future<Integer> insertIndexer(InMemoryDocumentStoreMetadataRepository repository) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)));
	}
}
