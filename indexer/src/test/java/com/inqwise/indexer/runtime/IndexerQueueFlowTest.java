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
import java.util.concurrent.atomic.AtomicReference;

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
	void preActionHookFailurePreventsWrite(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		IndexerModel model = documentModel();
		PutDocumentActionItem item = documentItem("42", "Ada");
		AtomicReference<DocumentActionExecutionContext> observed = new AtomicReference<>();
		DocumentActionRuntimeHooks hooks = new DocumentActionRuntimeHooks() {
			@Override
			public Future<Void> beforeAction(DocumentActionExecutionContext context) {
				observed.set(context);
				context.document().put("name", "mutated copy");
				return Future.failedFuture("pre-action rejected");
			}
		};
		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			store,
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.ACTION_ITEM_FAILED) {
					testContext.verify(() -> {
						assertEquals("pre-action rejected", event.getError().getMessage());
						assertNull(store.get("customers_1", "42"));
						assertEquals(10, observed.get().targetId());
						assertEquals(20, observed.get().indexerId());
						assertEquals("Ada", observed.get().document().getString("name"));
						testContext.completeNow();
					});
				}
				return Future.succeededFuture();
			},
			hooks
		);

		indexer.activate()
			.compose(ignored -> queue.publisher("customers_1"))
			.compose(publisher -> publisher.publish(item).eventually(publisher::close))
			.onFailure(testContext::failNow);
	}

	@Test
	void postWriteHookFailureLeavesItemForRedelivery(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		IndexerModel model = documentModel();
		PutDocumentActionItem item = documentItem("42", "Ada");
		AtomicInteger attempts = new AtomicInteger();
		AtomicInteger received = new AtomicInteger();
		AtomicReference<Indexer> first = new AtomicReference<>();
		DocumentActionRuntimeHooks hooks = new DocumentActionRuntimeHooks() {
			@Override
			public Future<Void> afterWriteBeforeCommit(
				DocumentActionExecutionContext context
			) {
				return attempts.incrementAndGet() == 1
					? Future.failedFuture("post-write rejected")
					: Future.succeededFuture();
			}
		};
		IndexerEventPublisher firstEvents = event -> {
			if (event.getType() == IndexerEventType.ACTION_ITEM_RECEIVED) {
				received.incrementAndGet();
			}
			if (event.getType() == IndexerEventType.ACTION_ITEM_FAILED) {
				testContext.verify(() -> {
					assertEquals("post-write rejected", event.getError().getMessage());
					assertEquals("Ada", store.get("customers_1", "42").getString("name"));
				});
				first.get().close().compose(ignored -> {
					Indexer recovered = new Indexer(
						vertx,
						model,
						queue,
						store,
						new IndexerOptions(),
						recoveredEvent -> {
							if (recoveredEvent.getType()
								== IndexerEventType.ACTION_ITEM_RECEIVED) {
								received.incrementAndGet();
							}
							if (recoveredEvent.getType()
								== IndexerEventType.CONSUMER_RESUMED
								&& recoveredEvent.getItem() != null) {
								testContext.verify(() -> {
									assertEquals(2, received.get());
									assertEquals(2, attempts.get());
									assertEquals(
										"Ada",
										store.get("customers_1", "42").getString("name")
									);
									testContext.completeNow();
								});
							}
							return Future.succeededFuture();
						},
						hooks
					);
					return recovered.activate();
				}).onFailure(testContext::failNow);
			}
			return Future.succeededFuture();
		};
		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			store,
			new IndexerOptions(),
			firstEvents,
			hooks
		);
		first.set(indexer);

		indexer.activate()
			.compose(ignored -> queue.publisher("customers_1"))
			.compose(publisher -> publisher.publish(item).eventually(publisher::close))
			.onFailure(testContext::failNow);
	}

	@Test
	void afterCommitObserverFailureDoesNotBlockNextItem(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		IndexerModel model = documentModel();
		AtomicInteger firstReceived = new AtomicInteger();
		AtomicInteger observerFailures = new AtomicInteger();
		AtomicInteger actionFailures = new AtomicInteger();
		DocumentActionRuntimeHooks hooks = new DocumentActionRuntimeHooks() {
			@Override
			public Future<Void> afterCommit(DocumentActionExecutionContext context) {
				return "41".equals(context.documentUid())
					? Future.failedFuture("observer unavailable")
					: Future.succeededFuture();
			}
		};
		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			store,
			new IndexerOptions(),
				event -> {
				if (event.getType() == IndexerEventType.ACTION_ITEM_FAILED) {
					actionFailures.incrementAndGet();
				}
				if (event.getType() == IndexerEventType.ACTION_ITEM_RECEIVED
					&& "41".equals(((PutDocumentActionItem) event.getItem()).getUid())) {
					firstReceived.incrementAndGet();
				}
				if (event.getType()
					== IndexerEventType.ACTION_ITEM_AFTER_COMMIT_OBSERVER_FAILED) {
					observerFailures.incrementAndGet();
					testContext.verify(() -> {
						assertEquals("observer unavailable", event.getError().getMessage());
						assertEquals("First", store.get("customers_1", "41").getString("name"));
					});
				}
				if (event.getType() == IndexerEventType.CONSUMER_RESUMED
					&& event.getItem() instanceof PutDocumentActionItem put
					&& "42".equals(put.getUid())) {
					testContext.verify(() -> {
						assertEquals(1, firstReceived.get());
						assertEquals(1, observerFailures.get());
						assertEquals(0, actionFailures.get());
						assertEquals("Second", store.get("customers_1", "42").getString("name"));
						testContext.completeNow();
					});
				}
				return Future.succeededFuture();
			},
			hooks
		);

		indexer.activate()
			.compose(ignored -> queue.publisher("customers_1"))
			.compose(publisher -> publisher.publish(documentItem("41", "First"))
				.compose(ignored -> publisher.publish(documentItem("42", "Second")))
				.eventually(publisher::close))
			.onFailure(testContext::failNow);
	}

	private IndexerModel documentModel() {
		return IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.build();
	}

	private PutDocumentActionItem documentItem(String uid, String name) {
		return IndexerActionItems.concretePutDocument(
			10,
			20,
			"customers_1",
			uid,
			new JsonObject().put("name", name)
		);
	}

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
	void failedActionRemainsPendingUntilRuntimeRecovery(
		Vertx vertx,
		VertxTestContext testContext
	) {
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
		PutDocumentActionItem nextItem = IndexerActionItems.concretePutDocument(
			10,
			20,
			"customers_1",
			"42",
			new JsonObject().put("name", "Ada")
		);
		AtomicInteger receivedItems = new AtomicInteger();
		AtomicReference<Indexer> indexerReference = new AtomicReference<>();

		Indexer indexer = new Indexer(
			vertx,
			model,
			queue,
			new InMemoryIndexerDocumentStore(),
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.ACTION_ITEM_RECEIVED) {
					receivedItems.incrementAndGet();
				}

				if (event.getType() == IndexerEventType.ACTION_ITEM_FAILED) {
					testContext.verify(() -> {
						assertEquals(item.toJson(), event.getItem().toJson());
						assertEquals(
							"Complete index action requires LOAD_WRITER role",
							event.getError().getMessage()
						);
					});

					queue.publisher("customers_1")
						.compose(publisher -> publisher.publish(nextItem))
						.compose(ignored -> indexerReference.get().close())
						.compose(ignored -> recoverRuntime(
							vertx,
							model,
							queue,
							item,
							receivedItems,
							testContext
						))
						.onFailure(testContext::failNow);
				}

				return Future.succeededFuture();
			}
		);
		indexerReference.set(indexer);

		indexer.activate()
			.compose(ignored -> queue.publisher("customers_1"))
			.compose(publisher -> publisher.publish(item))
			.onFailure(testContext::failNow);
	}

	private Future<Void> recoverRuntime(
		Vertx vertx,
		IndexerModel model,
		InMemoryIndexerQueue queue,
		CompleteIndexActionItem failedItem,
		AtomicInteger receivedItems,
		VertxTestContext testContext
	) {
		testContext.verify(() -> assertEquals(1, receivedItems.get()));
		Indexer recovered = new Indexer(
			vertx,
			model,
			queue,
			new InMemoryIndexerDocumentStore(),
			new IndexerOptions(),
			event -> {
				if (event.getType() == IndexerEventType.ACTION_ITEM_RECEIVED) {
					testContext.verify(() -> {
						assertEquals(failedItem.toJson(), event.getItem().toJson());
						assertEquals(1, receivedItems.get());
						testContext.completeNow();
					});
				}

				return Future.succeededFuture();
			}
		);

		return recovered.activate();
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
