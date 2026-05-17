package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertPublication;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReadinessState;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexPublicationCommandTest {
	@Test
	void markIndexReadyUpdatesPublicationReadiness(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		insertPublication(repository, ReadinessState.PENDING)
			.compose(publicationId -> commandService.submit(new MarkIndexReadyCommand(
				publicationId,
				"baseline loaded",
				0L
			)).compose(ignored -> repository.getPublicationById(publicationId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(ReadinessState.READY, found.get().readinessState());
				assertEquals("baseline loaded", found.get().reason());
				assertNotNull(found.get().readyAt());
				testContext.completeNow();
			})));
	}

	@Test
	void publishFailsWhenIndexIsNotReady(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		insertPublishableIndexer(repository, MutationState.WRITABLE, ReadinessState.PENDING)
			.compose(indexerId -> commandService.submit(new PublishIndexCommand(indexerId, 0L)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Index is not ready"));
				testContext.completeNow();
			})));
	}

	@Test
	void publishChangesIndexerPublicationState(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		insertPublishableIndexer(repository, MutationState.WRITABLE, ReadinessState.READY)
			.compose(indexerId -> commandService.submit(new PublishIndexCommand(indexerId, 0L))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(PublicationState.PUBLISHED, found.get().publicationState());
				assertEquals(1L, found.get().version());
				testContext.completeNow();
			})));
	}

	@Test
	void publishFailsWhenIndexerIsDeleting(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		insertPublishableIndexer(repository, MutationState.DELETING, ReadinessState.READY)
			.compose(indexerId -> commandService.submit(new PublishIndexCommand(indexerId, 0L)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Index is deleting"));
				testContext.completeNow();
			})));
	}

	@Test
	void retireChangesPublicationStateToRetired(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		insertPublishedIndexer(repository)
			.compose(indexerId -> commandService.submit(new RetireIndexCommand(indexerId, 0L))
				.compose(ignored -> repository.getIndexerById(indexerId)))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals(PublicationState.RETIRED, found.get().publicationState());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(InMemoryDocumentStoreMetadataRepository repository) {
		return new InMemoryCommandService()
			.register(new MarkIndexReadyCommandHandler(repository))
			.register(new PublishIndexCommandHandler(repository))
			.register(new RetireIndexCommandHandler(repository));
	}

	private Future<Integer> insertPublication(
		InMemoryDocumentStoreMetadataRepository repository,
		ReadinessState readinessState
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeStatus.STARTED,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> repository.insertPublication(new InsertPublication(
				null,
				indexerId,
				targetId,
				"customers",
				"customers_1",
				readinessState,
				null
			))));
	}

	private Future<Integer> insertPublishableIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		MutationState mutationState,
		ReadinessState readinessState
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeStatus.STARTED,
				PublicationState.UNPUBLISHED,
				mutationState
			)).compose(indexerId -> repository.insertPublication(new InsertPublication(
				null,
				indexerId,
				targetId,
				"customers",
				"customers_1",
				readinessState,
				null
			)).map(indexerId)));
	}

	private Future<Integer> insertPublishedIndexer(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		return repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeStatus.STARTED,
				PublicationState.PUBLISHED,
				MutationState.READ_ONLY
			)));
	}
}
