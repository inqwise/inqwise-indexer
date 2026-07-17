package com.inqwise.indexer.rest.target;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.targets.InitialPublicationMode;
import com.inqwise.indexer.service.target.TargetCatalogServiceVerticle;
import com.inqwise.indexer.service.target.TargetCatalogServices;
import com.inqwise.indexer.testing.RecordingTargetCatalog;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetCatalogRestVerticleTest {
	@Test
	void exposesTargetCatalogOnlyThroughConfiguredProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingTargetCatalog catalog = new RecordingTargetCatalog();
		String address = TargetCatalogServices.address("rest-test");
		TargetCatalogRestVerticle rest = new TargetCatalogRestVerticle(
			new TargetCatalogRestOptions().setPort(0).setServiceAddress(address)
		);

		vertx.deployVerticle(new TargetCatalogServiceVerticle(catalog, catalog, address))
			.compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.GET,
				"/targets?target_name=customers&status=ACTIVE",
				null,
				200
			))
			.compose(list -> {
				assertEquals(1, list.getJsonArray("targets").size());
				return request(vertx, rest.actualPort(), HttpMethod.GET, "/targets/17", null, 200);
			})
			.compose(found -> {
				assertEquals("target-17", found.getJsonObject("target").getString("uid"));
				return request(
					vertx,
					rest.actualPort(),
					HttpMethod.POST,
					"/targets",
					new JsonObject()
						.put("target_name", "customers")
						.put("timestamp", "2026-01-15T00:00:00Z")
						.put("initial_publication_mode", "READY"),
					201
				);
			})
			.compose(created -> request(
				vertx,
				rest.actualPort(),
				HttpMethod.POST,
				"/targets/17/recover-provisioning?expected_version=3",
				null,
				200
			))
			.onComplete(testContext.succeeding(recovered -> testContext.verify(() -> {
				assertEquals(17, recovered.getJsonObject("target").getInteger("id"));
				assertEquals(InitialPublicationMode.READY, catalog.created()
					.createIndexer().initialPublicationMode());
				assertEquals(3L, catalog.recovered().expectedVersion());
				testContext.completeNow();
			})));
	}

	private Future<JsonObject> request(
		Vertx vertx,
		int port,
		HttpMethod method,
		String uri,
		JsonObject body,
		int expectedStatus
	) {
		return vertx.createHttpClient()
			.request(method, port, "127.0.0.1", uri)
			.compose(request -> body == null ? request.send() : request
				.putHeader("content-type", "application/json")
				.send(body.encode()))
			.compose(response -> {
				assertEquals(expectedStatus, response.statusCode());
				return response.body().map(buffer -> buffer.toJsonObject());
			});
	}
}
