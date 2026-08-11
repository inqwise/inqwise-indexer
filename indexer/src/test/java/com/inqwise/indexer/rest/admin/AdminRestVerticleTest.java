package com.inqwise.indexer.rest.admin;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.testing.TestMetadataChangeNotifiers;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.cleanup.CleanupDeletingIndexerCommandHandler;
import com.inqwise.indexer.cleanup.CleanupResetIndexerQueueCommandHandler;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.provisioning.definitions.IndexerDefinition;
import com.inqwise.indexer.provisioning.definitions.QueueDefinition;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.adapters.local.StaticTargetDefinitionProvider;
import com.inqwise.indexer.adapters.local.InMemoryInvalidRouteCache;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MetadataIndexerOperations;
import com.inqwise.indexer.service.admin.AdminCreateRequestResolver;
import com.inqwise.indexer.service.admin.AdminInfrastructureItemView;
import com.inqwise.indexer.service.admin.AdminInfrastructureStatusResult;
import com.inqwise.indexer.service.admin.AdminInfrastructureStatusSource;
import com.inqwise.indexer.service.admin.AdminNodeServiceView;
import com.inqwise.indexer.service.admin.AdminNodeStatusResult;
import com.inqwise.indexer.service.admin.AdminNodeStatusSource;
import com.inqwise.indexer.service.admin.AdminServiceVerticle;
import com.inqwise.indexer.routing.InvalidRouteSignature;

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
	void exposesCatalogReadAliasesOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

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
			)).map(indexerId -> new CatalogFixture(targetId, indexerId)))
			.compose(fixture -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> get(
					vertx,
					restVerticle.actualPort(),
					"/admin/catalog/targets?target_name=customers"
				))
				.compose(targetsBody -> {
					JsonObject targets = targetsBody.toJsonObject();
					assertEquals(1, targets.getJsonArray("targets").size());
					assertEquals(
						fixture.targetId(),
						targets.getJsonArray("targets").getJsonObject(0).getInteger("id")
					);
					return get(
						vertx,
						restVerticle.actualPort(),
						"/admin/catalog/indexers/" + fixture.indexerId()
					);
				}))
			.onComplete(testContext.succeeding(indexerBody -> testContext.verify(() -> {
				JsonObject indexer = indexerBody.toJsonObject().getJsonObject("indexer");
				assertEquals("customers-index", indexer.getString("index_name"));
				assertEquals("customers-queue", indexer.getString("queue_name"));
				testContext.completeNow();
			})));
	}

	@Test
	void exposesLoadedDefinitionsOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);

		vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
			.compose(ignored -> vertx.deployVerticle(restVerticle))
			.compose(ignored -> get(
				vertx,
				restVerticle.actualPort(),
				"/admin/definitions/targets"
			))
			.compose(targetsBody -> {
				JsonObject targets = targetsBody.toJsonObject();
				assertEquals(1, targets.getJsonArray("target_definitions").size());
				assertEquals(
					"customers",
					targets.getJsonArray("target_definitions")
						.getJsonObject(0)
						.getString("target_name")
				);
				return get(
					vertx,
					restVerticle.actualPort(),
					"/admin/definitions/indexers/default"
				);
			})
			.onComplete(testContext.succeeding(indexerBody -> testContext.verify(() -> {
				JsonObject indexer = indexerBody.toJsonObject()
					.getJsonObject("indexer_definition");
				JsonObject index = indexer.getJsonObject("index");
				assertEquals("default", indexer.getString("name"));
				assertEquals("customers", index.getString("schema_name"));
				assertEquals("v1", index.getString("schema_version"));
				testContext.completeNow();
			})));
	}

	@Test
	void exposesRoutingDiagnosticsOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryInvalidRouteCache invalidRouteCache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		InMemoryTargetInvalidationRegistry targetInvalidations =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), java.time.Clock.systemUTC());
		AdminRestVerticle restVerticle = restVerticle(repository);

		invalidRouteCache.record(InvalidRouteSignature.builder()
			.withTargetName("customers")
			.withPeriodKey("2026-08")
			.withActionType(IndexerActionType.PUT_DOCUMENT)
			.build(), "missing writable indexer");

		targetInvalidations.markInvalidated(42)
			.compose(ignored -> vertx.deployVerticle(adminVerticle(
				repository,
				eventBus,
				queue,
				invalidRouteCache,
				targetInvalidations
			)))
			.compose(ignored -> vertx.deployVerticle(restVerticle))
			.compose(ignored -> get(
				vertx,
				restVerticle.actualPort(),
				"/admin/routing/invalid-routes?max=10"
			))
			.compose(routesBody -> {
				JsonObject routes = routesBody.toJsonObject();
				assertEquals(1, routes.getJsonArray("invalid_routes").size());
				JsonObject route = routes.getJsonArray("invalid_routes").getJsonObject(0);
				assertEquals(
					"customers",
					route.getJsonObject("signature").getString("target_name")
				);
				assertEquals("missing writable indexer", route.getString("reason"));
				return get(
					vertx,
					restVerticle.actualPort(),
					"/admin/routing/target-invalidations?max=10"
				);
			})
			.onComplete(testContext.succeeding(invalidationsBody -> testContext.verify(() -> {
				JsonObject invalidations = invalidationsBody.toJsonObject();
				assertEquals(1, invalidations.getJsonArray("target_invalidations").size());
				assertEquals(
					42,
					invalidations.getJsonArray("target_invalidations")
						.getJsonObject(0)
						.getInteger("target_id")
				);
				testContext.completeNow();
			})));
	}

	@Test
	void exposesNodeStatusOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);
		AdminNodeStatusSource nodeStatus = () -> AdminNodeStatusResult.builder()
			.withStarted(true)
			.withReady(true)
			.withRecoveryOnly(false)
			.withStopping(false)
			.withClustered(false)
			.withDeploymentCount(2)
			.withControlPlaneDeployments(1)
			.withDataPlaneDeployments(1)
			.withInfrastructureDeployments(0)
			.withLifecycleEventNamespace("local")
			.withTargetInvalidationProvider("VERTX_SHARED_DATA")
			.withTargetInvalidationNamespace("local")
			.withTargetInvalidationMaxTargets(100)
			.withServices(List.of(AdminNodeServiceView.builder()
				.withName("admin")
				.withGroup("control-plane")
				.withEnabled(true)
				.withConfiguredInstances(1)
				.withDeployedInstances(1)
				.build()))
			.build();

		vertx.deployVerticle(adminVerticle(
			repository,
			eventBus,
			queue,
			null,
			null,
			nodeStatus
		))
			.compose(ignored -> vertx.deployVerticle(restVerticle))
			.compose(ignored -> get(
				vertx,
				restVerticle.actualPort(),
				"/admin/node/status"
			))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject status = body.toJsonObject();
				assertEquals(true, status.getBoolean("ready"));
				assertEquals(2, status.getInteger("deployment_count"));
				assertEquals("local", status.getString("lifecycle_event_namespace"));
				assertEquals(1, status.getJsonArray("services").size());
				assertEquals(
					"admin",
					status.getJsonArray("services").getJsonObject(0).getString("name")
				);
				testContext.completeNow();
			})));
	}

	@Test
	void exposesInfrastructureStatusOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		AdminRestVerticle restVerticle = restVerticle(repository);
		AdminInfrastructureStatusSource infrastructureStatus = () ->
			AdminInfrastructureStatusResult.builder()
				.withItems(List.of(AdminInfrastructureItemView.builder()
					.withName("command-engine")
					.withCategory("command")
					.withImplementation("com.example.CommandEngine")
					.withDetails(new JsonObject().put("started", true))
					.build()))
				.build();

		vertx.deployVerticle(adminVerticle(
			repository,
			eventBus,
			queue,
			null,
			null,
			null,
			infrastructureStatus
		))
			.compose(ignored -> vertx.deployVerticle(restVerticle))
			.compose(ignored -> get(
				vertx,
				restVerticle.actualPort(),
				"/admin/infrastructure/status"
			))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject status = body.toJsonObject();
				JsonObject item = status.getJsonArray("items").getJsonObject(0);
				assertEquals("command-engine", item.getString("name"));
				assertEquals("command", item.getString("category"));
				assertEquals("com.example.CommandEngine", item.getString("implementation"));
				assertEquals(true, item.getJsonObject("details").getBoolean("started"));
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
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> request(
					vertx,
					HttpMethod.DELETE,
					restVerticle.actualPort(),
					"/admin/indexers/" + indexerId + "?expected_version=0",
					202
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
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> post(
					vertx,
					restVerticle.actualPort(),
					"/admin/indexers/" + indexerId + "/deactivate?expected_version=0"
				))
				.compose(deactivatedBody -> {
					JsonObject deactivated = deactivatedBody.toJsonObject().getJsonObject("indexer");
					assertEquals(IndexerRuntimeState.NON_ACTIVE.name(), deactivated.getString("runtime_state"));
					return post(
						vertx,
						restVerticle.actualPort(),
						"/admin/indexers/" + indexerId + "/activate?expected_version=1"
					);
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
						.put("initial_publication_mode", "READY")),
				201
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

		repository.insertTarget(readyTarget("test", "customers"))
			.compose(targetId -> vertx.deployVerticle(adminVerticle(repository, eventBus, queue))
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> request(
					vertx,
					HttpMethod.POST,
					restVerticle.actualPort(),
					"/admin/indexers",
					new JsonObject().put("target_id", targetId),
					201
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
		return request(vertx, method, port, uri, 200);
	}

	private io.vertx.core.Future<Buffer> request(
		Vertx vertx,
		HttpMethod method,
		int port,
		String uri,
		int expectedStatus
	) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> request.send()
				.compose(response -> {
					assertEquals(expectedStatus, response.statusCode());
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
		return request(vertx, method, port, uri, body, 200);
	}

	private io.vertx.core.Future<Buffer> request(
		Vertx vertx,
		HttpMethod method,
		int port,
		String uri,
		JsonObject body,
		int expectedStatus
	) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> request
				.putHeader("content-type", "application/json")
				.send(body.encode())
				.compose(response -> {
					assertEquals(expectedStatus, response.statusCode());
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
		return adminVerticle(
			repository,
			eventBus,
			queue,
			null,
			null,
			null,
			null
		);
	}

	private AdminServiceVerticle adminVerticle(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus,
		InMemoryIndexerQueue queue,
		InMemoryInvalidRouteCache invalidRouteCache,
		InMemoryTargetInvalidationRegistry targetInvalidations
	) {
		return adminVerticle(
			repository,
			eventBus,
			queue,
			invalidRouteCache,
			targetInvalidations,
			null,
			null
		);
	}

	private AdminServiceVerticle adminVerticle(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus,
		InMemoryIndexerQueue queue,
		InMemoryInvalidRouteCache invalidRouteCache,
		InMemoryTargetInvalidationRegistry targetInvalidations,
		AdminNodeStatusSource nodeStatus
	) {
		return adminVerticle(
			repository,
			eventBus,
			queue,
			invalidRouteCache,
			targetInvalidations,
			nodeStatus,
			null
		);
	}

	private AdminServiceVerticle adminVerticle(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus,
		InMemoryIndexerQueue queue,
		InMemoryInvalidRouteCache invalidRouteCache,
		InMemoryTargetInvalidationRegistry targetInvalidations,
		AdminNodeStatusSource nodeStatus,
		AdminInfrastructureStatusSource infrastructureStatus
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
				TargetDefinition.builder()
					.withTargetName("customers")
					.withPeriodStrategy(TargetPeriodStrategy.MONTHLY)
					.build()
			)),
			new StaticIndexerDefinitionProvider(IndexerDefinition.builder()
				.withIndex(IndexDefinition.builder()
					.withSchemaName("customers")
					.withSchemaVersion("v1")
					.withSettings(new JsonObject())
					.withMappings(new JsonObject())
					.build())
				.withQueue(QueueDefinition.builder()
					.withSettings(new JsonObject())
					.build())
				.build()),
			documentStore,
			commandService,
			indexerOperations,
			com.inqwise.indexer.monitoring.IndexerOperationalMonitor.NOOP,
			invalidRouteCache,
			targetInvalidations,
			nodeStatus,
			infrastructureStatus
		);
	}

	private record CatalogFixture(
		Integer targetId,
		Integer indexerId
	) {
	}
}
