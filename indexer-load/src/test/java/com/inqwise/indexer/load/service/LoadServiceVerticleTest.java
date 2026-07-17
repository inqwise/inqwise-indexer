package com.inqwise.indexer.load.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.testing.RecordingLoadManagementService;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadServiceVerticleTest {
	@Test
	void invokesLoadDomainThroughNamespacedProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingLoadManagementService domain = new RecordingLoadManagementService();
		String address = LoadServices.address("tenant-a");

		vertx.deployVerticle(new LoadServiceVerticle(domain, address))
			.compose(ignored -> LoadServices.proxy(vertx, address).create(new LoadCreateRequest()
				.setProviderId("archive")
				.setTargetId(11)
				.setLiveWriterPolicy(LiveWriterPolicy.CREATE_IMMEDIATELY)
				.setSourceFrom(Instant.parse("2026-01-01T00:00:00Z"))
				.setSourceQuery(new JsonObject().put("region", "eu"))
				.setReviewRequired(true)))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("archive", domain.created().providerId());
				assertEquals(11, domain.created().targetId());
				assertEquals("eu", domain.created().sourceQuery().getString("region"));
				assertEquals(91, result.getLoad().getInteger("indexer_id"));
				assertEquals("CREATED", result.getLoad().getString("state"));
				assertEquals(4L, result.getLoad().getLong("version"));
				testContext.completeNow();
			})));
	}
}
