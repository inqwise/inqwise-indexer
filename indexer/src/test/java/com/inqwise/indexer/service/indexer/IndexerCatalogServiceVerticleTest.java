package com.inqwise.indexer.service.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.testing.RecordingIndexerCatalog;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerCatalogServiceVerticleTest {
	@Test
	void invokesIndexerCatalogThroughNamespacedProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingIndexerCatalog catalog = new RecordingIndexerCatalog();
		String address = IndexerCatalogServices.address("tenant-a");
		IndexerCatalogService proxy = IndexerCatalogServices.proxy(vertx, address);

		vertx.deployVerticle(new IndexerCatalogServiceVerticle(catalog, catalog, address))
			.compose(ignored -> proxy.list(new IndexerQuery()
				.setTargetIds(List.of(17))
				.setRoles(List.of(IndexerRole.LIVE_WRITER))))
			.compose(list -> {
				assertEquals(1, list.getIndexers().size());
				return proxy.get(new IndexerGetRequest().setUid("indexer-29"));
			})
			.compose(found -> {
				assertEquals(29, found.getIndexer().getId());
				return proxy.activate(new IndexerVersionRequest()
					.setIndexerId(29)
					.setExpectedVersion(3L));
			})
			.compose(activated -> {
				assertEquals(IndexerRuntimeState.ACTIVE, activated.getIndexer().getRuntimeState());
				return proxy.deactivate(new IndexerVersionRequest()
					.setIndexerId(29)
					.setExpectedVersion(4L));
			})
			.onComplete(testContext.succeeding(deactivated -> testContext.verify(() -> {
				assertEquals(IndexerRuntimeState.NON_ACTIVE, deactivated.getIndexer().getRuntimeState());
				assertEquals(List.of(17), catalog.query().targetIds());
				assertEquals(3L, catalog.activated().expectedVersion());
				assertEquals(4L, catalog.deactivated().expectedVersion());
				testContext.completeNow();
			})));
	}
}
