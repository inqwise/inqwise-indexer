package com.inqwise.indexer.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.commands.InitialPublicationMode;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.definitions.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.definitions.StaticTargetDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;

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

		repository.insertTarget(new InsertTarget(null, "customers", null))
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

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
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

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
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
					new AdminIndexerLifecycleRequest().setIndexerId(indexerId)
				))
				.compose(activated -> {
					assertEquals(IndexerRuntimeState.ACTIVE, activated.getIndexer().getRuntimeState());
					assertEquals(1L, activated.getIndexer().getVersion());
					return AdminServices.proxy(vertx).deactivateIndexer(
						new AdminIndexerLifecycleRequest().setIndexerId(indexerId)
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

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
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

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
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
				.setPrefix("target-customers")
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
				.setPrefix("target-customers")
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

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> AdminServices.proxy(vertx).createIndexer(new AdminCreateIndexerRequest()
					.setPrefix("indexer-customers")
					.setTargetId(targetId)
					.setTargetName("customers")
					.setIndexName("customers-index")
					.setQueueName("customers-queue"))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("customers", result.getIndexer().getTargetName());
				assertEquals("customers-index", result.getIndexer().getIndexName());
				assertEquals("customers-queue", result.getIndexer().getQueueName());
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
		return new AdminServiceVerticle(
			repository,
			eventBus,
			queue,
			new StaticTargetDefinitionProvider(List.of(
				new TargetDefinition("customers", TargetPeriodStrategy.MONTHLY)
			)),
			new StaticIndexerDefinitionProvider(new IndexerDefinition(
				new IndexDefinition("customers", "v1", new JsonObject(), new JsonObject()),
				new QueueDefinition(new JsonObject())
			)),
			new InMemoryIndexerDocumentStore()
		);
	}

	private record CreatedTargetAndIndexer(
		AdminTargetResult target,
		com.inqwise.indexer.metadata.IndexerRecord indexer
	) {
	}
}
