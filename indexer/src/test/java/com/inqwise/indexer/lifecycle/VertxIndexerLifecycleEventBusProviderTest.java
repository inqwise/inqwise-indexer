package com.inqwise.indexer.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusConfig;
import com.inqwise.indexer.lifecycle.IndexerLifecycleProviderSignal;
import com.inqwise.indexer.lifecycle.IndexerLifecycleSubscription;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;
import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class VertxIndexerLifecycleEventBusProviderTest {
	@Test
	void publishesIndexerAndTargetEventsWithinNamespace(
		Vertx vertx,
		VertxTestContext testContext
	) {
		VertxIndexerLifecycleEventBusProvider publisherProvider =
			new VertxIndexerLifecycleEventBusProvider(vertx);
		VertxIndexerLifecycleEventBusProvider subscriberProvider =
			new VertxIndexerLifecycleEventBusProvider(vertx);
		IndexerLifecycleEventBus publisher = publisherProvider.create(config("production"));
		IndexerLifecycleEventBus subscriber = subscriberProvider.create(config("production"));
		IndexerLifecycleEventBus isolated = subscriberProvider.create(config("staging"));
		Promise<IndexerMetadataChanged> indexerReceived = Promise.promise();
		Promise<TargetMetadataChanged> targetReceived = Promise.promise();
		AtomicInteger isolatedEvents = new AtomicInteger();

		subscriber.subscribe(indexerReceived::tryComplete)
			.compose(indexerSubscription -> subscriber.subscribeTarget(targetReceived::tryComplete)
				.map(targetSubscription -> List.of(indexerSubscription, targetSubscription)))
			.compose(subscriptions -> isolated.subscribe(event -> isolatedEvents.incrementAndGet())
				.map(isolatedSubscription -> {
					List<IndexerLifecycleSubscription> all = new ArrayList<>(subscriptions);
					all.add(isolatedSubscription);
					return all;
				}))
			.compose(subscriptions -> {
				publisher.publish(indexerEvent(17));
				publisher.publish(targetEvent(10));
				return Future.all(indexerReceived.future(), targetReceived.future())
					.map(subscriptions);
			})
			.compose(this::closeAll)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				IndexerMetadataChanged indexer = indexerReceived.future().result();
				TargetMetadataChanged target = targetReceived.future().result();
				assertEquals(17, indexer.getIndexerId());
				assertEquals(3L, indexer.getVersion());
				assertEquals(10, target.getTargetId());
				assertEquals("customers", target.getTargetName());
				assertEquals(0, isolatedEvents.get());
				testContext.completeNow();
			})));
	}

	@Test
	void closedConsumerReceivesNoLaterEvents(Vertx vertx, VertxTestContext testContext) {
		IndexerLifecycleEventBus bus = new VertxIndexerLifecycleEventBusProvider(vertx)
			.create(config("production"));
		AtomicInteger received = new AtomicInteger();

		bus.subscribe(event -> received.incrementAndGet())
			.compose(IndexerLifecycleSubscription::close)
			.compose(ignored -> bus.publish(indexerEvent(17)))
			.compose(ignored -> delay(vertx, 20L))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(0, received.get());
				testContext.completeNow();
			})));
	}

	@Test
	void emitsThrottledLocalLagSignals(Vertx vertx, VertxTestContext testContext) {
		AtomicLong currentTime = new AtomicLong(1_000L);
		VertxIndexerLifecycleEventBusProvider provider =
			new VertxIndexerLifecycleEventBusProvider(
				vertx,
				new VertxIndexerLifecycleEventBusOptions()
					.setMaxTransportLagMs(100L)
					.setSignalCooldownMs(1_000L),
				currentTime::get
			);
		IndexerLifecycleEventBus bus = provider.create(config("production"));
		List<IndexerLifecycleProviderSignal> signals = new ArrayList<>();
		AtomicInteger events = new AtomicInteger();
		Promise<Void> firstEvent = Promise.promise();
		Promise<Void> secondEvent = Promise.promise();
		Promise<Void> secondSignal = Promise.promise();

		bus.subscribeProviderSignals(signal -> {
			signals.add(signal);
			if (signals.size() == 2) {
				secondSignal.tryComplete();
			}
		}).compose(signalSubscription -> bus.subscribe(event -> {
			int count = events.incrementAndGet();
			if (count == 1) {
				firstEvent.tryComplete();
			} else if (count == 2) {
				secondEvent.tryComplete();
			}
		}).map(eventSubscription -> List.of(signalSubscription, eventSubscription)))
			.compose(subscriptions -> {
				currentTime.set(1_200L);
				publishIndexerEnvelope(vertx, "production", indexerEvent(1), 1_000L);
				return firstEvent.future().map(subscriptions);
			})
			.compose(subscriptions -> {
				testContext.verify(() -> assertEquals(
					List.of(IndexerLifecycleProviderSignal.EXCESSIVE_LAG),
					signals
				));
				currentTime.set(1_500L);
				publishIndexerEnvelope(vertx, "production", indexerEvent(2), 1_300L);
				return secondEvent.future().map(subscriptions);
			})
			.compose(subscriptions -> {
				testContext.verify(() -> assertEquals(1, signals.size()));
				currentTime.set(2_500L);
				publishIndexerEnvelope(vertx, "production", indexerEvent(3), 2_300L);
				return secondSignal.future().map(subscriptions);
			})
			.compose(this::closeAll)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(
					List.of(
						IndexerLifecycleProviderSignal.EXCESSIVE_LAG,
						IndexerLifecycleProviderSignal.EXCESSIVE_LAG
					),
					signals
				);
				testContext.completeNow();
			})));
	}

	@Test
	void malformedEnvelopeEmitsConcreteDeliveryLossSignal(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerLifecycleEventBus bus = new VertxIndexerLifecycleEventBusProvider(vertx)
			.create(config("production"));
		Promise<IndexerLifecycleProviderSignal> signalReceived = Promise.promise();

		bus.subscribeProviderSignals(signalReceived::tryComplete)
			.compose(signalSubscription -> bus.subscribe(event -> {
				throw new AssertionError("Malformed event must not reach subscriber");
			}).map(eventSubscription -> List.of(signalSubscription, eventSubscription)))
			.compose(subscriptions -> {
				vertx.eventBus().publish(
					VertxIndexerLifecycleEventBus.indexerAddress("production"),
					new JsonObject().put("payload", new JsonObject())
				);
				return signalReceived.future().map(subscriptions);
			})
			.compose(this::closeAll)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(
					IndexerLifecycleProviderSignal.DELIVERY_LOST,
					signalReceived.future().result()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void acceptsLocalHealthSignalsFromDeploymentMetrics(
		Vertx vertx,
		VertxTestContext testContext
	) {
		VertxIndexerLifecycleEventBusProvider provider =
			new VertxIndexerLifecycleEventBusProvider(vertx);
		IndexerLifecycleEventBusConfig config = config("production");
		IndexerLifecycleEventBus bus = provider.create(config);
		Promise<IndexerLifecycleProviderSignal> received = Promise.promise();

		bus.subscribeProviderSignals(received::tryComplete)
			.onSuccess(ignored -> provider.reportProviderSignal(
				config,
				IndexerLifecycleProviderSignal.RECONNECTED
			))
			.compose(subscription -> received.future()
				.compose(signal -> subscription.close().map(signal)))
			.onComplete(testContext.succeeding(signal -> testContext.verify(() -> {
				assertEquals(IndexerLifecycleProviderSignal.RECONNECTED, signal);
				testContext.completeNow();
			})));
	}

	@Test
	void validatesLagOptions() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new VertxIndexerLifecycleEventBusOptions().setMaxTransportLagMs(0L)
				.validate()
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new VertxIndexerLifecycleEventBusOptions().setSignalCooldownMs(0L)
				.validate()
		);
	}

	private Future<Void> closeAll(List<IndexerLifecycleSubscription> subscriptions) {
		Future<Void> closed = Future.succeededFuture();
		for (int i = subscriptions.size() - 1; i >= 0; i--) {
			IndexerLifecycleSubscription subscription = subscriptions.get(i);
			closed = closed.compose(ignored -> subscription.close());
		}
		return closed;
	}

	private Future<Void> delay(Vertx vertx, long delayMs) {
		Promise<Void> delayed = Promise.promise();
		vertx.setTimer(delayMs, ignored -> delayed.tryComplete());
		return delayed.future();
	}

	private void publishIndexerEnvelope(
		Vertx vertx,
		String namespace,
		IndexerMetadataChanged event,
		long publishedAt
	) {
		vertx.eventBus().publish(
			VertxIndexerLifecycleEventBus.indexerAddress(namespace),
			new JsonObject()
				.put("published_at_epoch_ms", publishedAt)
				.put("payload", event.toJson())
		);
	}

	private IndexerLifecycleEventBusConfig config(String namespace) {
		return new IndexerLifecycleEventBusConfig(namespace);
	}

	private IndexerMetadataChanged indexerEvent(Integer indexerId) {
		return new IndexerMetadataChanged(indexerId, 10, "test", 3L);
	}

	private TargetMetadataChanged targetEvent(Integer targetId) {
		return new TargetMetadataChanged(
			targetId,
			"customers",
			"2026-06-30",
			"test",
			4L
		);
	}
}
