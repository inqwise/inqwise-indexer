package com.inqwise.indexer.service.target;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.targets.InitialPublicationMode;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.testing.RecordingTargetCatalog;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetCatalogServiceVerticleTest {
	@Test
	void invokesTargetCatalogThroughNamespacedProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingTargetCatalog catalog = new RecordingTargetCatalog();
		String address = TargetCatalogServices.address("tenant-a");
		TargetCatalogService proxy = TargetCatalogServices.proxy(vertx, address);

		vertx.deployVerticle(new TargetCatalogServiceVerticle(catalog, catalog, address))
			.compose(ignored -> proxy.list(new TargetQuery()
				.setTargetNames(List.of("customers"))
				.setStatuses(List.of(TargetStatus.ACTIVE))))
			.compose(list -> {
				assertEquals(1, list.getTargets().size());
				return proxy.get(new TargetGetRequest().setUid("target-17"));
			})
			.compose(found -> {
				assertEquals(17, found.getTarget().getId());
				return proxy.create(new TargetCreateRequest()
					.setTargetName("customers")
					.setTimestamp(Instant.parse("2026-01-15T00:00:00Z"))
					.setInitialPublicationMode(InitialPublicationMode.PUBLISH));
			})
			.compose(created -> {
				assertEquals("customers", created.getTarget().getTargetName());
				return proxy.recoverProvisioning(new TargetVersionRequest()
					.setTargetId(17)
					.setExpectedVersion(3L));
			})
			.onComplete(testContext.succeeding(recovered -> testContext.verify(() -> {
				assertEquals(17, recovered.getTarget().getId());
				assertEquals(List.of("customers"), catalog.query().targetNames());
				assertEquals(InitialPublicationMode.PUBLISH, catalog.created()
					.createIndexer().initialPublicationMode());
				assertNotNull(catalog.created().createIndexer().prefix());
				assertNotNull(catalog.created().createIndexer().indexName());
				assertNotNull(catalog.created().createIndexer().queueName());
				assertEquals(3L, catalog.recovered().expectedVersion());
				testContext.completeNow();
			})));
	}
}
