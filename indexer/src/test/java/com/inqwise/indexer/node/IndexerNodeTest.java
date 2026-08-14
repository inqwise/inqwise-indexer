package com.inqwise.indexer.node;

import static com.inqwise.indexer.testing.TestMetadataRecords.indexerRecord;
import static com.inqwise.indexer.testing.TestMetadataRecords.readyTarget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.gateway.GatewayAuditEvent;
import com.inqwise.indexer.gateway.GatewayAuditOutcome;
import com.inqwise.indexer.gateway.GatewayRequestHooks;
import com.inqwise.indexer.gateway.GatewayRequestMetadata;
import com.inqwise.indexer.gateway.GatewayPrincipal;
import com.inqwise.indexer.gateway.GatewayRestOptions;
import com.inqwise.indexer.routing.InvalidRouteSignature;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.rest.action.TargetActionRestOptions;
import com.inqwise.indexer.rest.admin.AdminRestOptions;
import com.inqwise.indexer.rest.runtime.RuntimeRestOptions;
import com.inqwise.indexer.service.admin.AdminServices;
import com.inqwise.indexer.service.runtime.RuntimeServices;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerNodeTest {
	@Test
	void startsAndStopsCoreInfrastructure(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNode node = IndexerNode.create(vertx, disabledServices());
		InMemoryCommandEngine engine = (InMemoryCommandEngine) node.components().commandEngine();

		node.start()
			.compose(ignored -> {
				assertTrue(engine.isStarted());
				assertTrue(node.components().targetInvalidationPoller().isStarted());
				assertTrue(node.isReady());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertFalse(engine.isStarted());
				assertFalse(node.components().targetInvalidationPoller().isStarted());
				assertFalse(node.isReady());
				testContext.completeNow();
			})));
	}

	@Test
	void deploysEnabledServices(Vertx vertx, VertxTestContext testContext) {
		IndexerNode node = IndexerNode.create(vertx, new IndexerNodeOptions());

		node.start()
			.compose(ignored -> AdminServices.proxy(vertx).listTargets(null))
			.compose(targets -> {
				assertEquals(0, targets.getTargets().size());
				return RuntimeServices.proxy(vertx).status();
			})
			.compose(status -> {
				assertEquals(0, status.getIndexers().size());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void reportsNodeStatusFromAdminService(Vertx vertx, VertxTestContext testContext) {
		IndexerNodeOptions options = disabledServices()
			.setService(
				IndexerNodeOptions.Services.ADMIN,
				IndexerServiceDeploymentOptions.builder().build()
			)
			.setService(
				IndexerNodeOptions.Services.RUNTIME,
				IndexerServiceDeploymentOptions.builder().build()
			);
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> AdminServices.proxy(vertx).nodeStatus())
			.compose(status -> {
				JsonObject json = status.toJson();
				assertEquals(true, json.getBoolean("started"));
				assertEquals(true, json.getBoolean("ready"));
				assertEquals(3, json.getInteger("deployment_count"));
				assertEquals(1, json.getInteger("control_plane_deployments"));
				assertEquals(1, json.getInteger("data_plane_deployments"));
				assertEquals(1, json.getInteger("infrastructure_deployments"));
				JsonObject admin = service(json, IndexerNodeOptions.Services.ADMIN);
				JsonObject runtime = service(json, IndexerNodeOptions.Services.RUNTIME);
				assertEquals(1, admin.getInteger("deployed_instances"));
				assertEquals(1, runtime.getInteger("deployed_instances"));
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void reportsInfrastructureStatusFromAdminService(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeOptions options = disabledServices()
			.setService(
				IndexerNodeOptions.Services.ADMIN,
				IndexerServiceDeploymentOptions.builder().build()
			);
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> AdminServices.proxy(vertx).infrastructureStatus())
			.compose(status -> {
				JsonObject json = status.toJson();
				JsonObject commandEngine = infrastructureItem(json, "command-engine");
				JsonObject invalidations = infrastructureItem(
					json,
					"target-invalidation-registry"
				);
				assertEquals("command", commandEngine.getString("category"));
				assertEquals(
					InMemoryCommandEngine.class.getName(),
					commandEngine.getString("implementation")
				);
				assertEquals(true, commandEngine.getJsonObject("details").getBoolean("started"));
				assertEquals("invalidation", invalidations.getString("category"));
				assertEquals(
					"local",
					invalidations.getJsonObject("details").getString("namespace")
				);
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysTargetInvalidationServiceBeforeProxyClientUse(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeOptions options = disabledServices();
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> node.components().targetInvalidationRegistry().markInvalidated(10))
			.compose(ignored -> node.components().targetInvalidationRegistry().listInvalidations(10))
			.compose(entries -> {
				assertEquals(1, entries.entries().size());
				assertEquals(10, entries.entries().get(0).concreteTargetId());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void stoppedNodeDoesNotReceiveLaterMetadataEvents(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNode node = IndexerNode.create(vertx, disabledServices());
		InvalidRouteSignature route = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			IndexerActionType.PUT_DOCUMENT
		);

		node.start()
			.compose(ignored -> node.stop())
			.compose(ignored -> {
				node.components().invalidRouteCache().record(route, "missing target");
				return node.components().lifecycleEventBus().publish(new TargetMetadataChanged(
					10,
					"customers",
					null,
					"target.changed",
					1L
				));
			})
			.compose(ignored -> delay(vertx, 20L))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(node.components().invalidRouteCache().find(route).isPresent());
				testContext.completeNow();
			})));
	}

	@Test
	void recoveryOnlyModeKeepsControlPlaneAndCanRestoreDataPlane(
		Vertx vertx,
		VertxTestContext testContext
	) throws IOException {
		int healthPort = availablePort();
		int adminPort = availablePort();
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setService(
				IndexerNodeOptions.Services.HEALTH_REST,
				IndexerServiceDeploymentOptions.builder().build()
			)
			.setHealthRestOptions(
				NodeHealthRestOptions.builder().withPort(healthPort).build()
			)
			.setService(
				IndexerNodeOptions.Services.ADMIN_REST,
				IndexerServiceDeploymentOptions.builder().build()
			)
			.setAdminRestOptions(AdminRestOptions.builder().withPort(adminPort).build());
		IndexerNode node = IndexerNode.create(vertx, options);
		int[] activeDeployments = new int[1];

		node.start()
			.compose(ignored -> {
				activeDeployments[0] = node.deploymentIds().size();
				return node.components().runtimeReconciler().stop();
			})
			.compose(ignored -> node.enterRecoveryOnly(new IllegalStateException("test failure")))
			.compose(ignored -> {
				assertTrue(node.isRecoveryOnly());
				assertFalse(node.isReady());
				assertTrue(node.deploymentIds().size() < activeDeployments[0]);
				assertTrue(node.deploymentIds().size() > 0);
				assertTrue(((InMemoryCommandEngine) node.components().commandEngine()).isStarted());
				return healthStatus(
					vertx,
					healthPort,
					NodeHealthRestVerticle.READY_PATH
				);
			})
			.compose(status -> {
				assertEquals(503, status);
				return healthStatus(
					vertx,
					healthPort,
					NodeHealthRestVerticle.LIVE_PATH
				);
			})
			.compose(status -> {
				assertEquals(204, status);
				return recoverNode(vertx, adminPort);
			})
			.compose(recovered -> {
				assertTrue(recovered.getBoolean("ready"));
				assertFalse(recovered.getBoolean("recovery_only"));
				assertFalse(node.isRecoveryOnly());
				assertTrue(node.isReady());
				assertEquals(activeDeployments[0], node.deploymentIds().size());
				return recoverNode(vertx, adminPort);
			})
			.compose(repeated -> {
				assertTrue(repeated.getBoolean("ready"));
				assertFalse(repeated.getBoolean("recovery_only"));
				return healthStatus(
					vertx,
					healthPort,
					NodeHealthRestVerticle.READY_PATH
				);
			})
			.compose(status -> {
				assertEquals(200, status);
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private Future<JsonObject> recoverNode(Vertx vertx, int port) {
		return vertx.createHttpClient()
			.request(HttpMethod.POST, port, "127.0.0.1", "/admin/node/recover")
			.compose(request -> request.send())
			.compose(response -> {
				assertEquals(200, response.statusCode());
				return response.body();
			})
			.map(body -> body.toJsonObject());
	}

	@Test
	void defaultNodeInvalidatesRouteCacheFromMetadataEvents(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNode node = IndexerNode.create(vertx, new IndexerNodeOptions());
		InvalidRouteSignature route = new InvalidRouteSignature(
			"customers",
			null,
			null,
			null,
			null,
			IndexerActionType.PUT_DOCUMENT
		);

		node.components().invalidRouteCache().record(route, "missing target");
		node.start()
			.compose(ignored -> node.components().repository()
				.insertTarget(readyTarget("test", "customers")))
			.compose(targetId -> node.components().repository()
				.insertIndexer(indexerRecord(
					"test",
					targetId,
					"customers",
					"customers_1",
					"queue-customers-1",
					IndexerType.INDEX,
					IndexerRuntimeState.ACTIVE,
					PublicationState.UNPUBLISHED,
					MutationState.WRITABLE
				)).compose(indexerId -> node.components().lifecycleEventBus()
						.publish(new IndexerMetadataChanged(
							indexerId,
							targetId,
							"indexer.changed",
							0L
						))))
			.compose(ignored -> delay(vertx, 20L))
			.compose(ignored -> {
				assertTrue(node.components().invalidRouteCache().find(route).isEmpty());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysAdminRestWhenEnabled(Vertx vertx, VertxTestContext testContext) throws IOException {
		int port = availablePort();
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setService(IndexerNodeOptions.Services.ADMIN_REST, new IndexerServiceDeploymentOptions())
			.setAdminRestOptions(new AdminRestOptions().setPort(port));
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, port, "127.0.0.1", "/admin/targets")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}))
			.compose(body -> {
				assertEquals(0, body.toJsonObject().getJsonArray("targets").size());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysTargetActionRestWhenEnabled(Vertx vertx, VertxTestContext testContext) throws IOException {
		int port = availablePort();
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setService(IndexerNodeOptions.Services.TARGET_ACTION_REST, new IndexerServiceDeploymentOptions())
			.setTargetActionRestOptions(new TargetActionRestOptions().setPort(port));
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.POST, port, "127.0.0.1", "/targets/customers/actions")
				.compose(request -> request
					.putHeader("content-type", "application/json")
					.send(new JsonObject().encode()))
				.compose(response -> {
					assertEquals(400, response.statusCode());
					return response.body();
				}))
			.compose(body -> node.stop())
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysGatewayWhenEnabled(Vertx vertx, VertxTestContext testContext) throws IOException {
		int port = availablePort();
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setService(IndexerNodeOptions.Services.GATEWAY, new IndexerServiceDeploymentOptions())
			.setGatewayOptions(new GatewayRestOptions()
				.setPort(port)
				.setAdminRestBaseUri("http://127.0.0.1:8080"));
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, port, "127.0.0.1", "/gateway/status")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}))
			.compose(body -> {
				JsonObject status = body.toJsonObject();
				assertEquals("UP", status.getString("status"));
				assertEquals(true, status.getBoolean("admin_rest_configured"));
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysGatewayWithNodeSuppliedPolicy(
		Vertx vertx,
		VertxTestContext testContext
	) throws IOException {
		int port = availablePort();
		AtomicInteger auditFailures = new AtomicInteger();
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setService(
				IndexerNodeOptions.Services.GATEWAY,
				IndexerServiceDeploymentOptions.builder().build()
			)
			.setGatewayOptions(GatewayRestOptions.builder().withPort(port).build());
		GatewayRequestHooks hooks = new GatewayRequestHooks() {
			@Override
			public Future<Void> authorize(
				GatewayRequestMetadata request,
				GatewayPrincipal principal
			) {
				return Future.failedFuture("denied");
			}

			@Override
			public Future<Void> audit(GatewayAuditEvent event) {
				if (event.outcome() == GatewayAuditOutcome.FAILURE) {
					auditFailures.incrementAndGet();
				}
				return Future.succeededFuture();
			}
		};
		IndexerNode node = IndexerNode.create(vertx, options, hooks);

		node.start()
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, port, "127.0.0.1", "/gateway/status")
				.compose(request -> request.send()))
			.compose(response -> {
				assertEquals(403, response.statusCode());
				assertEquals(1, auditFailures.get());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysGatewayProxyToAdminRestWhenEnabled(Vertx vertx, VertxTestContext testContext) throws IOException {
		int adminRestPort = availablePort();
		int gatewayPort = availablePort();
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setService(IndexerNodeOptions.Services.ADMIN_REST, new IndexerServiceDeploymentOptions())
			.setAdminRestOptions(new AdminRestOptions().setPort(adminRestPort))
			.setService(IndexerNodeOptions.Services.GATEWAY, new IndexerServiceDeploymentOptions())
			.setGatewayOptions(new GatewayRestOptions()
				.setPort(gatewayPort)
				.setAdminRestBaseUri("http://127.0.0.1:" + adminRestPort));
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, gatewayPort, "127.0.0.1", "/gateway/admin/targets")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}))
			.compose(body -> {
				assertEquals(0, body.toJsonObject().getJsonArray("targets").size());
				return vertx.createHttpClient()
					.request(HttpMethod.GET, gatewayPort, "127.0.0.1", "/gateway/admin/indexers")
					.compose(request -> request.send())
					.compose(response -> {
						assertEquals(200, response.statusCode());
						return response.body();
					});
			})
			.compose(body -> {
				assertEquals(0, body.toJsonObject().getJsonArray("indexers").size());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysRuntimeRestWhenEnabled(Vertx vertx, VertxTestContext testContext) throws IOException {
		int port = availablePort();
		IndexerNodeOptions options = new IndexerNodeOptions()
			.setService(IndexerNodeOptions.Services.RUNTIME_REST, new IndexerServiceDeploymentOptions())
			.setRuntimeRestOptions(new RuntimeRestOptions().setPort(port));
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, port, "127.0.0.1", "/runtime/status")
				.compose(request -> request.send())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}))
			.compose(body -> {
				assertEquals(0, body.toJsonObject().getJsonArray("indexers").size());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deploysNodeHealthRestWhenEnabled(
		Vertx vertx,
		VertxTestContext testContext
	) throws IOException {
		int port = availablePort();
		IndexerNodeOptions options = disabledServices()
			.setService(
				IndexerNodeOptions.Services.HEALTH_REST,
				IndexerServiceDeploymentOptions.builder().build()
			)
			.setHealthRestOptions(NodeHealthRestOptions.builder().withPort(port).build());
		IndexerNode node = IndexerNode.create(vertx, options);

		node.start()
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, port, "127.0.0.1", NodeHealthRestVerticle.LIVE_PATH)
				.compose(request -> request.send()))
			.compose(response -> {
				assertEquals(204, response.statusCode());
				return vertx.createHttpClient()
					.request(HttpMethod.GET, port, "127.0.0.1", NodeHealthRestVerticle.READY_PATH)
					.compose(request -> request.send());
			})
			.compose(response -> {
				assertEquals(200, response.statusCode());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private static int availablePort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}

	private static Future<Void> delay(Vertx vertx, long delayMs) {
		Promise<Void> delayed = Promise.promise();
		vertx.setTimer(delayMs, ignored -> delayed.tryComplete());
		return delayed.future();
	}

	private static Future<Integer> healthStatus(Vertx vertx, int port, String path) {
		return vertx.createHttpClient()
			.request(HttpMethod.GET, port, "127.0.0.1", path)
			.compose(request -> request.send())
			.map(response -> response.statusCode());
	}

	private static JsonObject service(JsonObject status, String name) {
		return status.getJsonArray("services").stream()
			.map(JsonObject.class::cast)
			.filter(service -> name.equals(service.getString("name")))
			.findFirst()
			.orElseThrow();
	}

	private static JsonObject infrastructureItem(JsonObject status, String name) {
		return status.getJsonArray("items").stream()
			.map(JsonObject.class::cast)
			.filter(item -> name.equals(item.getString("name")))
			.findFirst()
			.orElseThrow();
	}

	private static IndexerNodeOptions disabledServices() {
		IndexerNodeOptions options = new IndexerNodeOptions();
		for (String service : options.getServices().keySet()) {
			if (IndexerNodeOptions.Services.TARGET_INVALIDATION_REGISTRY.equals(service)) {
				continue;
			}
			options.setService(
				service,
				new IndexerServiceDeploymentOptions().setEnabled(false)
			);
		}
		return options;
	}
}
