package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class PreloadIndexerTest {
	@Test
	void forwardsPreloadBatchToReplacementIndexer(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		IndexerOptions options = new IndexerOptions();

		IndexerModel replacementModel = IndexerModel.builder()
			.withId(2)
			.withUid("replacement")
			.withTargetName("customers")
			.withIndexName("customers_2")
			.withType(IndexerType.INDEX)
			.build();

		Indexer replacement = new Indexer(vertx, replacementModel, store);

		IndexerModel preloadModel = IndexerModel.builder()
			.withId(1)
			.withUid("preload_customers")
			.withTargetName("customers")
			.withIndexName("customers_preload")
			.withType(IndexerType.PRELOAD)
			.build();

		PreloadIndexer preload = new PreloadIndexer(vertx, preloadModel, replacement, store, options);

		JsonArray batch = new JsonArray()
			.add(PutDocumentActionItem.builder()
				.withTargetName("customers")
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build()
				.toJson());

		DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader(PreloadIndexer.LAST_HEADER, "true");

		preload.activate()
			.compose(ignored -> vertx.eventBus().request(preloadModel.getUid(), batch, deliveryOptions))
			.onComplete(testContext.succeeding(message -> testContext.verify(() -> {
				assertEquals("Ada", store.get("customers_2", "42").getString("name"));
				assertTrue(preload.isCompleted());
				testContext.completeNow();
			})));
	}
}
