package com.inqwise.indexer.rest.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.service.indexer.IndexerCatalogServiceVerticle;
import com.inqwise.indexer.service.indexer.IndexerCatalogServices;
import com.inqwise.indexer.testing.RecordingIndexerCatalog;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerCatalogRestVerticleTest {
	@Test
	void exposesIndexerCatalogOnlyThroughConfiguredProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingIndexerCatalog catalog = new RecordingIndexerCatalog();
		String address = IndexerCatalogServices.address("rest-test");
		IndexerCatalogRestVerticle rest = new IndexerCatalogRestVerticle(
			new IndexerCatalogRestOptions().setPort(0).setServiceAddress(address)
		);

		vertx.deployVerticle(new IndexerCatalogServiceVerticle(catalog, catalog, address))
			.compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.GET,
				"/indexers?target_id=17&role=LIVE_WRITER&runtime_state=NON_ACTIVE",
				200
			))
			.compose(list -> {
				assertEquals(1, list.getJsonArray("indexers").size());
				return request(vertx, rest.actualPort(), HttpMethod.GET, "/indexers/29", 200);
			})
			.compose(found -> {
				assertEquals("indexer-29", found.getJsonObject("indexer").getString("uid"));
				return request(
					vertx,
					rest.actualPort(),
					HttpMethod.POST,
					"/indexers/29/activate?expected_version=3",
					200
				);
			})
			.compose(activated -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.POST,
				"/indexers/29/deactivate?expected_version=4",
				200
			))
			.onComplete(testContext.succeeding(deactivated -> testContext.verify(() -> {
				assertEquals(
					"NON_ACTIVE",
					deactivated.getJsonObject("indexer").getString("runtime_state")
				);
				assertEquals(3L, catalog.activated().expectedVersion());
				assertEquals(4L, catalog.deactivated().expectedVersion());
				testContext.completeNow();
			})));
	}

	private Future<JsonObject> request(
		Vertx vertx,
		int port,
		HttpMethod method,
		String uri,
		int expectedStatus
	) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> request.send())
			.compose(response -> {
				assertEquals(expectedStatus, response.statusCode());
				return response.body().map(buffer -> buffer.toJsonObject());
			});
	}
}
