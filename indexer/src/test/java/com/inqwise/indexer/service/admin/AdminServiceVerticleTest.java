package com.inqwise.indexer.service.admin;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.targets.InitialPublicationMode;
import com.inqwise.indexer.cleanup.CleanupDeletingIndexerCommandHandler;
import com.inqwise.indexer.cleanup.CleanupResetIndexerQueueCommandHandler;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;
import com.inqwise.indexer.provisioning.definitions.QueueDefinition;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.adapters.local.StaticTargetDefinitionProvider;
import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class AdminServiceVerticleTest {
	@Test
	void listsTargetsThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(ignored -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue)))
			.compose(ignored -> AdminServices.proxy(vertx).listTargets(new AdminTargetQuery()
				.setTargetNames(List.of("customers"))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(1, result.getTargets().size());
				AdminTargetView target = result.getTargets().get(0);
				assertEquals("customers", target.getTargetName());
				assertEquals(TargetStatus.ACTIVE, target.getStatus());
				testContext.completeNow();
			})));
	}

	@Test
	void listsAndGetsIndexersThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"runtime",
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> AdminServices.proxy(vertx).listIndexers(new AdminIndexerQuery()
					.setIds(List.of(indexerId))))
				.compose(result -> {
					assertEquals(1, result.getIndexers().size());
					assertEquals("customers-index", result.getIndexers().get(0).getIndexName());
					return AdminServices.proxy(vertx).getIndexer(new AdminIndexerGetRequest()
						.setId(indexerId));
				}))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("customers", result.getIndexer().getTargetName());
				assertEquals("customers-queue", result.getIndexer().getQueueName());
				assertEquals(PublicationState.PUBLISHED, result.getIndexer().getPublicationState());
				testContext.completeNow();
			})));
	}

	@Test
	void recoversTargetProvisioningThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		repository.insertTarget(new InsertTarget(
			"target-customers",
			"customers",
			null,
			null,
			null,
			TargetStatus.ACTIVE,
			TargetProvisioningState.FAILED
		))
			.compose(targetId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> AdminServices.proxy(vertx).recoverTargetProvisioning(
					new AdminRecoverTargetProvisioningRequest()
						.setTargetId(targetId)
						.setExpectedVersion(0L)
				)))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("customers", result.getTarget().getTargetName());
				assertEquals(TargetProvisioningState.READY, result.getTarget().getProvisioningState());
				assertEquals(1L, result.getTarget().getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void activatesAndDeactivatesIndexerThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"runtime",
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> AdminServices.proxy(vertx).activateIndexer(
					new AdminIndexerLifecycleRequest()
						.setIndexerId(indexerId)
						.setExpectedVersion(0L)
				))
				.compose(activated -> {
					assertEquals(IndexerRuntimeState.ACTIVE, activated.getIndexer().getRuntimeState());
					assertEquals(1L, activated.getIndexer().getVersion());
					return AdminServices.proxy(vertx).deactivateIndexer(
						new AdminIndexerLifecycleRequest()
							.setIndexerId(indexerId)
							.setExpectedVersion(1L)
					);
				}))
			.onComplete(testContext.succeeding(deactivated -> testContext.verify(() -> {
				assertEquals(IndexerRuntimeState.NON_ACTIVE, deactivated.getIndexer().getRuntimeState());
				assertEquals(2L, deactivated.getIndexer().getVersion());
				assertEquals(2, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void deletesIndexerThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"runtime",
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> AdminServices.proxy(vertx).deleteIndexer(
					new AdminDeleteIndexerRequest()
						.setIndexerId(indexerId)
						.setExpectedVersion(0L)
				)))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(MutationState.DELETING, result.getIndexer().getMutationState());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, result.getIndexer().getRuntimeState());
				assertEquals(2L, result.getIndexer().getVersion());
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void resetsIndexerQueueThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> repository.insertIndexer(indexerRecord(
				"runtime",
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> AdminServices.proxy(vertx).resetIndexerQueue(
					new AdminResetIndexerQueueRequest()
						.setIndexerId(indexerId)
						.setExpectedVersion(0L)
				)))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("customers-queue-v1", result.getIndexer().getQueueName());
				assertEquals(1L, result.getIndexer().getVersion());
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void createsTargetThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
			.compose(ignored -> AdminServices.proxy(vertx).createTarget(new AdminCreateTargetRequest()
				.setTargetName("customers")
				.setTimestamp(Instant.parse("2026-05-18T10:15:00Z"))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("customers", result.getTarget().getTargetName());
				assertEquals("2026-05", result.getTarget().getPeriodKey());
				assertEquals(TargetProvisioningState.READY, result.getTarget().getProvisioningState());
				assertEquals(1L, result.getTarget().getVersion());
				testContext.completeNow();
			})));
	}

	@Test
	void createsTargetWithIndexerThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
			.compose(ignored -> AdminServices.proxy(vertx).createTarget(new AdminCreateTargetRequest()
				.setTargetName("customers")
				.setTimestamp(Instant.parse("2026-05-18T10:15:00Z"))
				.setCreateIndexer(new AdminCreateTargetIndexerRequest()
					.setPrefix("indexer-customers")
					.setIndexName("customers-index")
					.setQueueName("customers-queue")
					.setInitialPublicationMode(InitialPublicationMode.READY))))
			.compose(target -> repository.listIndexersByTargetId(target.getTarget().getId())
				.map(indexers -> new CreatedTargetAndIndexer(target, indexers.get(0))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(TargetProvisioningState.READY, result.target().getTarget().getProvisioningState());
				assertEquals("customers-index", result.indexer().indexName());
				assertEquals("customers-queue", result.indexer().queueName());
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void createsIndexerThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> AdminServices.proxy(vertx).createIndexer(new AdminCreateIndexerRequest()
					.setPrefix("indexer-customers")
					.setTargetId(targetId)
					.setIndexName("customers-index")
					.setQueueName("customers-queue"))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("customers", result.getIndexer().getTargetName());
				assertEquals("customers-index", result.getIndexer().getIndexName());
				assertEquals("customers-queue", result.getIndexer().getQueueName());
				assertEquals(IndexerRole.LIVE_WRITER, result.getIndexer().getRole());
				assertEquals(IndexResourceOwnership.OWNER, result.getIndexer().getIndexOwnership());
				assertEquals(IndexerRuntimeState.NON_ACTIVE, result.getIndexer().getRuntimeState());
				assertEquals(1L, result.getIndexer().getVersion());
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	private AdminServiceVerticle adminVerticle(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus,
		InMemoryIndexerQueue queue
	) {
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		IndexerOperations indexerOperations = new MetadataIndexerOperations(
			repository,
			TestMetadataChangeNotifiers.create(eventBus)
		);
		InMemoryCommandEngine commandService = new InMemoryCommandEngine()
			.register(new CleanupResetIndexerQueueCommandHandler(queue))
			.register(new CleanupDeletingIndexerCommandHandler(
				repository,
				queue,
				documentStore
			));
		return new AdminServiceVerticle(
			repository,
			TestMetadataChangeNotifiers.create(eventBus),
			queue,
			new StaticTargetDefinitionProvider(List.of(
				new TargetDefinition("customers", TargetPeriodStrategy.MONTHLY)
			)),
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("customers", "v1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			)),
			documentStore,
			commandService,
			indexerOperations
		);
	}

	private record CreatedTargetAndIndexer(
		AdminTargetResult target,
		com.inqwise.indexer.metadata.IndexerRecord indexer
	) {
	}
}
