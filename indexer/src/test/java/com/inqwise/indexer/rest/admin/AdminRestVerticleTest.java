package com.inqwise.indexer.rest.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
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
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.service.admin.AdminCreateRequestResolver;
import com.inqwise.indexer.service.admin.AdminServiceVerticle;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class AdminRestVerticleTest {
	@Test
	void exposesAdminReadEndpointsOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

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
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> get(vertx, restVerticle.actualPort(), "/admin/targets?target_name=customers"))
				.compose(targetsBody -> {
					JsonObject targets = targetsBody.toJsonObject();
					assertEquals(1, targets.getJsonArray("targets").size());
					assertEquals("customers", targets.getJsonArray("targets").getJsonObject(0).getString("target_name"));
					return get(vertx, restVerticle.actualPort(), "/admin/indexers/" + indexerId);
				}))
			.onComplete(testContext.succeeding(indexerBody -> testContext.verify(() -> {
				JsonObject indexer = indexerBody.toJsonObject().getJsonObject("indexer");
				assertEquals("customers-index", indexer.getString("index_name"));
				assertEquals("customers-queue", indexer.getString("queue_name"));
				testContext.completeNow();
			})));
	}

	@Test
	void recoversTargetProvisioningOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

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
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> post(
					vertx,
					restVerticle.actualPort(),
					"/admin/targets/" + targetId + "/recover-provisioning?expected_version=0"
				)))
			.onComplete(testContext.succeeding(targetBody -> testContext.verify(() -> {
				JsonObject target = targetBody.toJsonObject().getJsonObject("target");
				assertEquals("customers", target.getString("target_name"));
				assertEquals(TargetProvisioningState.READY.name(), target.getString("provisioning_state"));
				assertEquals(1L, target.getLong("version"));
				testContext.completeNow();
			})));
	}

	@Test
	void resetsIndexerQueueOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

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
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> post(
					vertx,
					restVerticle.actualPort(),
					"/admin/indexers/" + indexerId + "/reset-queue?expected_version=0"
				)))
			.onComplete(testContext.succeeding(indexerBody -> testContext.verify(() -> {
				JsonObject indexer = indexerBody.toJsonObject().getJsonObject("indexer");
				assertEquals("customers-queue-v1", indexer.getString("queue_name"));
				assertEquals(1L, indexer.getLong("version"));
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void deletesIndexerOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

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
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> request(
					vertx,
					HttpMethod.DELETE,
					restVerticle.actualPort(),
					"/admin/indexers/" + indexerId + "?expected_version=0"
				)))
			.onComplete(testContext.succeeding(indexerBody -> testContext.verify(() -> {
				JsonObject indexer = indexerBody.toJsonObject().getJsonObject("indexer");
				assertEquals(MutationState.DELETING.name(), indexer.getString("mutation_state"));
				assertEquals(IndexerRuntimeState.NON_ACTIVE.name(), indexer.getString("runtime_state"));
				assertEquals(2L, indexer.getLong("version"));
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void activatesAndDeactivatesIndexerOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

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
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> post(vertx, restVerticle.actualPort(), "/admin/indexers/" + indexerId + "/deactivate"))
				.compose(deactivatedBody -> {
					JsonObject deactivated = deactivatedBody.toJsonObject().getJsonObject("indexer");
					assertEquals(IndexerRuntimeState.NON_ACTIVE.name(), deactivated.getString("runtime_state"));
					return post(vertx, restVerticle.actualPort(), "/admin/indexers/" + indexerId + "/activate");
				}))
			.onComplete(testContext.succeeding(activatedBody -> testContext.verify(() -> {
				JsonObject activated = activatedBody.toJsonObject().getJsonObject("indexer");
				assertEquals(IndexerRuntimeState.ACTIVE.name(), activated.getString("runtime_state"));
				assertEquals(2, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void createsTargetOverHttpWithDateAndGeneratedIndexer(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

		vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
			.compose(ignored -> vertx.deployVerticle(restVerticle))
			.compose(ignored -> request(
				vertx,
				HttpMethod.POST,
				restVerticle.actualPort(),
				"/admin/targets",
				new JsonObject()
					.put("target_name", "customers")
					.put("date", "2026-05-18")
					.put("create_indexer", new JsonObject()
						.put("initial_publication_mode", "READY"))
			))
			.compose(targetBody -> {
				JsonObject target = targetBody.toJsonObject().getJsonObject("target");
				assertEquals("customers", target.getString("target_name"));
				assertEquals("2026-05", target.getString("period_key"));
				assertEquals(TargetProvisioningState.READY.name(), target.getString("provisioning_state"));
				return repository.listIndexersByTargetId(target.getInteger("id"));
			})
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals("customers", indexers.get(0).targetName());
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	@Test
	void createsIndexerOverHttpFromTargetId(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> request(
					vertx,
					HttpMethod.POST,
					restVerticle.actualPort(),
					"/admin/indexers",
					new JsonObject().put("target_id", targetId)
				)))
			.onComplete(testContext.succeeding(indexerBody -> testContext.verify(() -> {
				JsonObject indexer = indexerBody.toJsonObject().getJsonObject("indexer");
				assertEquals("customers", indexer.getString("target_name"));
				assertEquals(IndexerRuntimeState.NON_ACTIVE.name(), indexer.getString("runtime_state"));
				assertEquals(PublicationState.UNPUBLISHED.name(), indexer.getString("publication_state"));
				assertEquals(MutationState.WRITABLE.name(), indexer.getString("mutation_state"));
				assertEquals(1, eventBus.events().size());
				testContext.completeNow();
			})));
	}

	private io.vertx.core.Future<Buffer> get(Vertx vertx, int port, String uri) {
		return request(vertx, HttpMethod.GET, port, uri);
	}

	private io.vertx.core.Future<Buffer> post(Vertx vertx, int port, String uri) {
		return request(vertx, HttpMethod.POST, port, uri);
	}

	private io.vertx.core.Future<Buffer> request(Vertx vertx, HttpMethod method, int port, String uri) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> request.send()
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}));
	}

	private io.vertx.core.Future<Buffer> request(
		Vertx vertx,
		HttpMethod method,
		int port,
		String uri,
		JsonObject body
	) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> request
				.putHeader("content-type", "application/json")
				.send(body.encode())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}));
	}

	private AdminRestVerticle restVerticle(InMemoryDocumentStoreMetadataRepository repository) {
		return new AdminRestVerticle(
			new AdminRestOptions().setPort(0),
			new AdminCreateRequestResolver(repository)
		);
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
}
