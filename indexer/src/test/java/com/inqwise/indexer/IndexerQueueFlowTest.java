package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

	@Test
	void deleteRemovesCurrentResourcesOnly(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerQueue currentQueue = new InMemoryIndexerQueue();
		InMemoryIndexerQueue nextQueue = new InMemoryIndexerQueue();
		IndexerModel currentModel = IndexerModel.builder()
			.withId(1)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.build();
		IndexerModel nextModel = IndexerModel.builder()
			.withId(2)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_2")
			.build();
		Indexer nextIndexer = new Indexer(
			vertx,
			nextModel,
			nextQueue,
			repository,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);
		Indexer indexer = new Indexer(
			vertx,
			currentModel,
			nextIndexer,
			currentQueue,
			repository,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		repository.save(currentModel)
			.compose(ignored -> repository.save(nextModel))
			.compose(ignored -> store.put("customers_1", "42", new JsonObject().put("name", "Ada")))
			.compose(ignored -> store.put("customers_2", "43", new JsonObject().put("name", "Grace")))
			.compose(ignored -> indexer.delete())
			.compose(ignored -> repository.get(1))
			.compose(deleted -> {
				assertTrue(deleted.isEmpty());
				assertNull(store.get("customers_1", "42"));
				assertEquals("Grace", store.get("customers_2", "43").getString("name"));
				return Future.succeededFuture();
			})
			.compose(ignored -> repository.get(2))
			.onComplete(testContext.succeeding(next -> testContext.verify(() -> {
				assertTrue(next.isPresent());
				assertEquals("customers_2", next.get().getIndexName());
				assertEquals(nextIndexer.status().toJson(), indexer.status().toJson().getJsonObject("next"));
				testContext.completeNow();
			})));
	}
}
