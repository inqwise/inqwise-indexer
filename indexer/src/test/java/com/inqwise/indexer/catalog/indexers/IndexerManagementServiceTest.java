package com.inqwise.indexer.catalog.indexers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.metadata.UpdateIndexerMutationState;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerManagementServiceTest {
	@Test
	void activateCommandUpdatesMetadataRepositoryAndPublishesEvent(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		IndexerManagementService service = service(repository, eventBus);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE))
			.compose(indexerId -> service.activate(new IndexerRuntimeStateRequest(indexerId, 0L))
				.compose(result -> {
					assertEquals(indexerId, result.indexerId());
					assertEquals(IndexerRuntimeState.ACTIVE, result.runtimeState());
					assertEquals(1L, result.version());
					return repository.getIndexerById(indexerId);
				}))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(IndexerRuntimeState.ACTIVE, found.get().runtimeState());
				assertEquals(1L, found.get().version());
				assertEquals(1, events.size());
				assertEquals("indexer.activate", events.get(0).getCommandType());
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
		IndexerManagementService service = service(repository, eventBus);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE))
			.compose(indexerId -> {
				IndexerRuntimeStateRequest activate = new IndexerRuntimeStateRequest(indexerId, 0L);
				IndexerRuntimeStateRequest deactivate = new IndexerRuntimeStateRequest(indexerId, 1L);
				return service.activate(activate)
					.compose(ignored -> service.activate(activate))
					.compose(ignored -> service.deactivate(deactivate))
					.compose(ignored -> service.deactivate(deactivate))
					.compose(result -> {
						assertEquals(IndexerRuntimeState.NON_ACTIVE, result.runtimeState());
						assertEquals(2L, result.version());
						return repository.getIndexerById(indexerId);
					});
			})
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
		IndexerManagementService service = service(repository, eventBus);

		insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE, MutationState.DELETING)
			.compose(indexerId -> service.activate(new IndexerRuntimeStateRequest(indexerId, 0L)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Cannot activate deleted indexer"));
				testContext.completeNow();
			})));
	}

	@Test
	void activateRedeliveryRejectsLaterInactiveVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerManagementService service = service(
			repository,
			new InMemoryIndexerLifecycleEventBus()
		);

		insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE)
			.compose(indexerId -> service.activate(new IndexerRuntimeStateRequest(indexerId, 0L))
				.compose(ignored -> service.deactivate(new IndexerRuntimeStateRequest(indexerId, 1L)))
				.compose(ignored -> service.activate(new IndexerRuntimeStateRequest(indexerId, 0L))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Indexer version conflict for id"));
				testContext.completeNow();
			})));
	}

	@Test
	void deactivateRedeliveryRejectsLaterActiveVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerManagementService service = service(
			repository,
			new InMemoryIndexerLifecycleEventBus()
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> service.deactivate(new IndexerRuntimeStateRequest(indexerId, 0L))
				.compose(ignored -> service.activate(new IndexerRuntimeStateRequest(indexerId, 1L)))
				.compose(ignored -> service.deactivate(new IndexerRuntimeStateRequest(indexerId, 0L))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Indexer version conflict for id"));
				testContext.completeNow();
			})));
	}

	@Test
	void deactivateDoesNotClaimDeletingTransition(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		IndexerManagementService service = service(
			repository,
			new InMemoryIndexerLifecycleEventBus()
		);

		insertIndexer(repository, IndexerRuntimeState.NON_ACTIVE)
			.compose(indexerId -> repository.updateIndexerMutationState(
				new UpdateIndexerMutationState(indexerId, MutationState.DELETING, 0L)
			).compose(ignored -> service.deactivate(new IndexerRuntimeStateRequest(
				indexerId,
				0L
			))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Indexer version conflict for id"));
				testContext.completeNow();
			})));
	}

	private IndexerManagementService service(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new MetadataIndexerManagementService(
			repository,
			TestMetadataChangeNotifiers.create(eventBus)
		);
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
		return repository.insertTarget(new InsertTarget("test", "customers", null))
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
