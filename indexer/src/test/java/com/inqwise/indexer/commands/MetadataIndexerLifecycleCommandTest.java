package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.TestMetadataChangeNotifiers;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.IndexerRuntimeState;
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
		InMemoryCommandEngine commandService = commandService(repository, eventBus);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE))
			.compose(indexerId -> commandService.submit(new ActivateIndexerCommand(indexerId))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerRuntimeState.ACTIVE, found.get().runtimeState());
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
		InMemoryCommandEngine commandService = commandService(repository, eventBus);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE))
			.compose(indexerId -> commandService.submit(new ActivateIndexerCommand(indexerId))
				.compose(ignored -> commandService.submit(new ActivateIndexerCommand(indexerId)))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(indexerId)))
				.compose(ignored -> commandService.submit(new DeactivateIndexerCommand(indexerId)))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, found.get().runtimeState());
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
		InMemoryCommandEngine commandService = commandService(repository, eventBus);

		insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE, MutationState.DELETING)
			.compose(indexerId -> commandService.submit(new ActivateIndexerCommand(indexerId)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Cannot activate deleted indexer"));
				testContext.completeNow();
			})));
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandEngine()
			.register(new ActivateIndexerCommandHandler(
				repository,
				TestMetadataChangeNotifiers.create(eventBus)
			))
			.register(new DeactivateIndexerCommandHandler(
				repository,
				TestMetadataChangeNotifiers.create(eventBus)
			));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRuntimeState runtimeState
	) {
		return insertIndexer(repository, runtimeState, MutationState.WRITABLE);
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRuntimeState runtimeState,
		MutationState mutationState
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				runtimeState,
				PublicationState.UNPUBLISHED,
				mutationState
			)));
	}
}
