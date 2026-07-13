package com.inqwise.indexer.rest.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.runtime.Indexer;
import com.inqwise.indexer.runtime.IndexerRuntime;
import com.inqwise.indexer.runtime.IndexerRuntimeReconciler;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.service.runtime.RuntimeServiceVerticle;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class RuntimeRestVerticleTest {
	@Test
	void reconcilesAndReportsRuntimeStatusOverHttp(Vertx vertx, VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		IndexerRuntime runtime = new IndexerRuntime(
			indexer -> new Indexer(
				vertx,
				IndexerRuntime.toModel(indexer),
				documentStore
			)
		);
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);
		RuntimeRestVerticle restVerticle = new RuntimeRestVerticle(
			new RuntimeRestOptions().setPort(0)
		);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				"runtime",
				targetId,
				"customers",
				"customers-index",
				"customers-queue",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)))
			.compose(indexerId -> reconciler.start()
				.compose(ignored -> vertx.deployVerticle(new RuntimeServiceVerticle(
					runtime,
					reconciler
				)))
				.compose(ignored -> vertx.deployVerticle(restVerticle))
				.compose(ignored -> post(
					vertx,
					restVerticle.actualPort(),
					"/runtime/indexers/" + indexerId + "/reconcile"
				))
				.compose(reconcileBody -> {
					assertEquals("ACCEPTED", reconcileBody.toJsonObject().getString("status"));
					return get(vertx, restVerticle.actualPort(), "/runtime/status");
				}))
			.onComplete(testContext.succeeding(statusBody -> testContext.verify(() -> {
				JsonObject status = statusBody.toJsonObject();
				assertEquals(1, status.getJsonArray("indexers").size());
				JsonObject indexer = status.getJsonArray("indexers").getJsonObject(0);
				assertEquals("customers", indexer.getString("target_name"));
				assertEquals("customers-index", indexer.getString("index_name"));
				assertEquals("customers-queue", indexer.getString("queue_name"));
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
}
