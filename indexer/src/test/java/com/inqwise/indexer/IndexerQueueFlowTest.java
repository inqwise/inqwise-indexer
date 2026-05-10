package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerQueueFlowTest {
	@Test
	void activateStartsIndexerOnce(Vertx vertx, VertxTestContext testContext) {
		AtomicInteger startedEvents = new AtomicInteger();
		IndexerModel model = IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.build();
		Indexer indexer = new Indexer(
			vertx,
			model,
			new InMemoryIndexerQueue(),
			new InMemoryIndexerDocumentStore(),
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.INDEXER_STARTED) {
					startedEvents.incrementAndGet();
				}

				return Future.succeededFuture();
			}
		);

		indexer.activate()
			.compose(ignored -> indexer.activate())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, startedEvents.get());
				testContext.completeNow();
			})));
	}

	@Test
	void putDocumentItemIsProcessedThroughQueueConsumer(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		IndexerModel model = IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.build();
		PutDocumentActionItem item = PutDocumentActionItem.builder()
			.withIndexName("customers_1")
			.withUid("42")
			.withDocument(new JsonObject().put("name", "Ada"))
			.build();

		Indexer indexer = new Indexer(vertx, model, queue, store, new IndexerOptions(), event -> {
			if (event.getType() == IndexerEventType.CONSUMER_RESUMED && event.getItem() != null) {
				testContext.verify(() -> {
					assertEquals("Ada", store.get("customers_1", "42").getString("name"));
					testContext.completeNow();
				});
			}

			return Future.succeededFuture();
		});

		indexer.activate()
			.compose(ignored -> queue.publish(item))
			.onFailure(testContext::failNow);
	}
}
