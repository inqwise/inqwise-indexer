package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.definitions.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.definitions.StaticTargetDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.metadata.ConcreteTargetKey;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class CreateTargetCommandTest {
	@Test
	void createsConcreteTargetWithoutIndexer(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		commandService.submit(new CreateTargetCommand(
			"target-customers",
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			null
		)).compose(ignored -> repository.getTargetByDefinitionAndPeriod(
			new ConcreteTargetKey("customers", "2026-05")
		)).onComplete(testContext.succeeding(found -> testContext.verify(() -> {
			assertTrue(found.isPresent());
			assertEquals(TargetProvisioningState.READY, found.get().provisioningState());
			assertEquals(1L, found.get().version());
			testContext.completeNow();
		})));
	}

	@Test
	void failsWhenConcreteTargetAlreadyExists(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		commandService.submit(new CreateTargetCommand(
			"target-customers",
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			null
		)).compose(ignored -> commandService.submit(new CreateTargetCommand(
			"target-customers-2",
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			null
		))).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertEquals("Target already exists: customers", error.getMessage());
			testContext.completeNow();
		})));
	}

	@Test
	void createsTargetWithReadyIndexer(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingDocumentIndexResourceManager documentResources =
			new RecordingDocumentIndexResourceManager();
		RecordingQueueResourceManager queueResources = new RecordingQueueResourceManager();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandService commandService = commandService(
			repository,
			documentResources,
			queueResources,
			eventBus
		);

		commandService.submit(new CreateTargetCommand(
			"target-customers",
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			createIndexer(InitialPublicationMode.READY)
		)).compose(ignored -> repository.getTargetByDefinitionAndPeriod(
			new ConcreteTargetKey("customers", "2026-05")
		)).compose(found -> {
			assertTrue(found.isPresent());
			return repository.listIndexersByTargetId(found.get().id());
		}).compose(indexers -> repository.getPublicationByIndexerId(indexers.get(0).id())
			.map(publication -> new ReadyResult(indexers.get(0), publication.orElseThrow())))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(IndexerProvisioningState.READY, result.indexer().provisioningState());
				assertEquals(PublicationState.UNPUBLISHED, result.indexer().publicationState());
				assertEquals(ReadinessState.READY, result.publication().readinessState());
				assertEquals(List.of("customers-index"), documentResources.ensured);
				assertEquals(List.of("customers-queue"), queueResources.ensured);
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void createsTargetWithPublishedIndexer(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryCommandService commandService = commandService(repository);

		commandService.submit(new CreateTargetCommand(
			"target-customers",
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			createIndexer(InitialPublicationMode.PUBLISH)
		)).compose(ignored -> repository.getTargetByDefinitionAndPeriod(
			new ConcreteTargetKey("customers", "2026-05")
		)).compose(found -> repository.listIndexersByTargetId(found.orElseThrow().id()))
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals(PublicationState.PUBLISHED, indexers.get(0).publicationState());
				testContext.completeNow();
			})));
	}

	@Test
	void marksTargetFailedWhenIndexerProvisioningFails(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingDocumentIndexResourceManager documentResources =
			new RecordingDocumentIndexResourceManager();
		documentResources.failure = new IllegalStateException("index create failed");
		InMemoryCommandService commandService = commandService(
			repository,
			documentResources,
			new RecordingQueueResourceManager(),
			new InMemoryIndexerLifecycleEventBus()
		);

		commandService.submit(new CreateTargetCommand(
			"target-customers",
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			createIndexer(InitialPublicationMode.READY)
		)).onComplete(testContext.failing(error -> repository.getTargetByDefinitionAndPeriod(
			new ConcreteTargetKey("customers", "2026-05")
		).onComplete(testContext.succeeding(found -> testContext.verify(() -> {
			assertEquals("index create failed", error.getMessage());
			assertTrue(found.isPresent());
			assertEquals(TargetProvisioningState.FAILED, found.get().provisioningState());
			testContext.completeNow();
		})))));
	}

	private InMemoryCommandService commandService(InMemoryDocumentStoreMetadataRepository repository) {
		return commandService(
			repository,
			new RecordingDocumentIndexResourceManager(),
			new RecordingQueueResourceManager(),
			new InMemoryIndexerLifecycleEventBus()
		);
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository repository,
		RecordingDocumentIndexResourceManager documentResources,
		RecordingQueueResourceManager queueResources,
		InMemoryIndexerLifecycleEventBus eventBus
	) {
		return new InMemoryCommandService()
			.register(new CreateTargetCommandHandler(
				repository,
				new StaticTargetDefinitionProvider(List.of(
					new TargetDefinition("customers", TargetPeriodStrategy.MONTHLY)
				)),
				new StaticIndexerDefinitionProvider(new IndexerDefinition(
					new IndexDefinition("customers", "v1", new JsonObject(), new JsonObject()),
					new QueueDefinition(new JsonObject())
				)),
				documentResources,
				queueResources,
				eventBus
			));
	}

	private CreateTargetCommand.CreateIndexer createIndexer(InitialPublicationMode mode) {
		return new CreateTargetCommand.CreateIndexer(
			"indexer-customers",
			"customers-index",
			"customers-queue",
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerRuntimeState.NON_ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE,
			mode
		);
	}

	private record ReadyResult(
		com.inqwise.indexer.metadata.IndexerRecord indexer,
		com.inqwise.indexer.metadata.PublicationRecord publication
	) {
	}

	private static class RecordingDocumentIndexResourceManager
		implements IndexerDocumentIndexResourceManager {
		private final List<String> ensured = new ArrayList<>();
		private Throwable failure;

		@Override
		public Future<Void> ensure(String indexName, IndexDefinition definition) {
			ensured.add(indexName);
			return failure == null ? Future.succeededFuture() : Future.failedFuture(failure);
		}

		@Override
		public Future<Void> delete(String indexName) {
			return Future.succeededFuture();
		}
	}

	private static class RecordingQueueResourceManager implements IndexerQueueResourceManager {
		private final List<String> ensured = new ArrayList<>();

		@Override
		public Future<Void> ensure(String queueName) {
			ensured.add(queueName);
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete(String queueName) {
			return Future.succeededFuture();
		}
	}
}
