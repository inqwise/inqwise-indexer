package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.gateway.GatewayRestOptions;
import com.inqwise.indexer.hot.InvalidRouteSignature;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
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
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertFalse(engine.isStarted());
				assertFalse(node.components().targetInvalidationPoller().isStarted());
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
	) {
		IndexerNode node = IndexerNode.create(vertx, new IndexerNodeOptions());
		int[] activeDeployments = new int[1];

		node.start()
			.compose(ignored -> {
				activeDeployments[0] = node.deploymentIds().size();
				return node.components().runtimeReconciler().stop();
			})
			.compose(ignored -> node.enterRecoveryOnly(new IllegalStateException("test failure")))
			.compose(ignored -> {
				assertTrue(node.isRecoveryOnly());
				assertTrue(node.deploymentIds().size() < activeDeployments[0]);
				assertTrue(node.deploymentIds().size() > 0);
				assertTrue(((InMemoryCommandEngine) node.components().commandEngine()).isStarted());
				return node.recover();
			})
			.compose(ignored -> {
				assertFalse(node.isRecoveryOnly());
				assertEquals(activeDeployments[0], node.deploymentIds().size());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
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
				.insertTarget(new InsertTarget(null, "customers", null)))
			.compose(targetId -> node.components().repository()
				.insertIndexer(new InsertIndexer(
					null,
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
