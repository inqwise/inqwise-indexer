package com.inqwise.indexer.lifecycle;

import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBusProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryIndexerLifecycleEventBusProviderTest {
	@Test
	void sharesNamespacesAndClosesSubscriptions(VertxTestContext testContext) {
		InMemoryIndexerLifecycleEventBusProvider provider =
			new InMemoryIndexerLifecycleEventBusProvider();
		IndexerLifecycleEventBus publisher = provider.create(
			new IndexerLifecycleEventBusConfig("production")
		);
		IndexerLifecycleEventBus subscriber = provider.create(
			new IndexerLifecycleEventBusConfig("production")
		);
		IndexerLifecycleEventBus isolated = provider.create(
			new IndexerLifecycleEventBusConfig("staging")
		);
		List<Integer> received = new ArrayList<>();
		List<Integer> isolatedReceived = new ArrayList<>();

		subscriber.subscribe(event -> received.add(event.getIndexerId()))
			.compose(subscription -> isolated.subscribe(
				event -> isolatedReceived.add(event.getIndexerId())
			).map(ignored -> subscription))
			.compose(subscription -> publisher.publish(indexerEvent(17))
				.compose(ignored -> subscription.close()))
			.compose(ignored -> publisher.publish(indexerEvent(18)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of(17), received);
				assertEquals(List.of(), isolatedReceived);
				testContext.completeNow();
			})));
	}

	@Test
	void deliversProviderSignalsUntilSubscriptionCloses(VertxTestContext testContext) {
		InMemoryIndexerLifecycleEventBus bus = new InMemoryIndexerLifecycleEventBus();
		List<IndexerLifecycleProviderSignal> received = new ArrayList<>();

		bus.subscribeProviderSignals(received::add)
			.compose(subscription -> {
				bus.emitProviderSignal(IndexerLifecycleProviderSignal.RECONNECTED);
				return subscription.close();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				bus.emitProviderSignal(IndexerLifecycleProviderSignal.EXCESSIVE_LAG);
				assertEquals(List.of(IndexerLifecycleProviderSignal.RECONNECTED), received);
				testContext.completeNow();
			})));
	}

	private IndexerMetadataChanged indexerEvent(Integer indexerId) {
		return new IndexerMetadataChanged(indexerId, 10, "test", 1L);
	}
}
