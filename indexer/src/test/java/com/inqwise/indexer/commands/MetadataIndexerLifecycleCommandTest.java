package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerLifecycleChanged;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class MetadataIndexerLifecycleCommandTest {
	@Test
	void activateCommandUpdatesMetadataRepositoryAndPublishesEvent(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeStatus.NON_ACTIVE))
			.compose(indexerId -> commandService.submit(new ActivateIndexerCommand(indexerId))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerRuntimeStatus.STARTED, found.get().runtimeStatus());
				assertEquals(1L, found.get().version());
				assertEquals(1, events.size());
				assertEquals(ActivateIndexerCommand.TYPE, events.get(0).getCommandType());
				assertEquals(1L, events.get(0).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void repeatedMetadataLifecycleCommandsAreIdempotentAndKeepVersionStable(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeStatus.NON_ACTIVE))
			.compose(indexerId -> commandService.submit(new ActivateIndexerCommand(indexerId))
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(indexerId)))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(indexerId)))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(indexerId)))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerRuntimeStatus.NON_ACTIVE, found.get().runtimeStatus());
				assertEquals(2L, found.get().version());
				assertEquals(4, events.size());
				assertEquals(1L, events.get(0).getVersion());
				assertEquals(1L, events.get(1).getVersion());
				assertEquals(2L, events.get(2).getVersion());
				assertEquals(2L, events.get(3).getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataActivateDeletedIndexerFails(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(repository, eventBus);

		insertIndexer(repository, IndexerRuntimeStatus.DELETED)
			.compose(indexerId -> commandService.submit(new ActivateIndexerCommand(indexerId)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Cannot activate deleted indexer"));
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandService()
			.register(new ActivateIndexerCommandHandler(repository, eventBus))
			.register(new DeactivateIndexerCommandHandler(repository, eventBus));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRuntimeStatus runtimeStatus
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				runtimeStatus,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)));
	}
}
