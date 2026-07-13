package com.inqwise.indexer.runtime;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.actions.CompleteIndexActionItem;
import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

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
			.withQueueName("customers_1")
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
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.build();
		PutDocumentActionItem item = IndexerActionItems.concretePutDocument(
			10,
			20,
			"customers_1",
			"42",
			new JsonObject().put("name", "Ada")
		);

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
			.compose(ignored -> queue.publisher("customers_1"))
			.compose(publisher -> publisher.publish(item))
			.onFailure(testContext::failNow);
	}

	@Test
	void completeActionItemFailsForLiveWriter(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.build();
		CompleteIndexActionItem item = CompleteIndexActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.build();

		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			new InMemoryIndexerDocumentStore(),
				new IndexerOptions(),
				event -> {
					if (event.getType() == IndexerEventType.ACTION_ITEM_FAILED) {
						testContext.verify(() -> {
							assertEquals(item.toJson(), event.getItem().toJson());
							assertEquals(
								"Complete index action requires LOAD_WRITER role",
								event.getError().getMessage()
							);
							testContext.completeNow();
						});
					}

				return Future.succeededFuture();
			}
		);

		indexer.activate()
			.compose(ignored -> queue.publisher("customers_1"))
			.compose(publisher -> publisher.publish(item))
			.onFailure(testContext::failNow);
	}

	@Test
	void inMemoryQueueIsolatesItemsByQueueName(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		IndexerModel model = IndexerModel.builder()
			.withId(21)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_b")
			.withQueueName("queue-b")
			.build();
		PutDocumentActionItem item = IndexerActionItems.concretePutDocument(
			10,
			21,
			"customers_b",
			"42",
			new JsonObject().put("name", "Ada")
		);

		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		indexer.activate()
			.compose(ignored -> queue.publisher("queue-a"))
			.compose(publisher -> publisher.publish(item).eventually(publisher::close))
			.onSuccess(ignored -> vertx.setTimer(20L, timer -> testContext.verify(() -> {
				assertNull(store.get("customers_b", "42"));
				testContext.completeNow();
			})))
			.onFailure(testContext::failNow);
	}

	@Test
	void deletingOneInMemoryQueueDoesNotDeleteAnother(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		IndexerModel model = IndexerModel.builder()
			.withId(21)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_b")
			.withQueueName("queue-b")
			.build();
		PutDocumentActionItem item = IndexerActionItems.concretePutDocument(
			10,
			21,
			"customers_b",
			"43",
			new JsonObject().put("name", "Grace")
		);

		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			store,
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.CONSUMER_RESUMED && event.getItem() != null) {
					testContext.verify(() -> {
						assertEquals("Grace", store.get("customers_b", "43").getString("name"));
						testContext.completeNow();
					});
				}

				return Future.succeededFuture();
			}
		);

		indexer.activate()
			.compose(ignored -> queue.ensure("queue-a"))
			.compose(ignored -> queue.delete("queue-a"))
			.compose(ignored -> queue.publisher("queue-b"))
			.compose(publisher -> publisher.publish(item).eventually(publisher::close))
			.onFailure(testContext::failNow);
	}

	@Test
	void deleteClosesRuntimeOnly(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue currentQueue = new InMemoryIndexerQueue();
		IndexerModel currentModel = IndexerModel.builder()
			.withId(1)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.build();
		Indexer indexer = new Indexer(
			vertx,
			currentModel,
			currentQueue,
			store,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP
		);

		store.put("customers_1", "42", new JsonObject().put("name", "Ada"))
			.compose(ignored -> indexer.delete())
			.compose(ignored -> {
				assertEquals("Ada", store.get("customers_1", "42").getString("name"));
				return Future.succeededFuture();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("customers_1", indexer.status().toJson().getString("index_name"));
				testContext.completeNow();
			})));
	}
}
