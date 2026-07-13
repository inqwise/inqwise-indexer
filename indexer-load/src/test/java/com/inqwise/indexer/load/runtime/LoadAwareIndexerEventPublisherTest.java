package com.inqwise.indexer.load.runtime;

import com.inqwise.indexer.load.adapters.local.InMemoryIndexerLoadRepository;
import com.inqwise.indexer.load.adapters.local.InMemoryLoadProviderRegistry;
import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.api.LoadProvider;
import com.inqwise.indexer.load.api.LoadRequest;
import com.inqwise.indexer.load.api.LoadStopRequest;
import com.inqwise.indexer.load.api.LoadWriter;
import com.inqwise.indexer.load.repository.InsertIndexerLoad;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.runtime.IndexerEvent;
import com.inqwise.indexer.runtime.IndexerEventType;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRole;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadAwareIndexerEventPublisherTest {
	@Test
	void failedActionMarksActiveLoadFailedAndStopsProvider(VertxTestContext testContext) {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry providers = new InMemoryLoadProviderRegistry()
			.register("history", provider);
		List<IndexerEvent> delegated = new ArrayList<>();
		LoadAwareIndexerEventPublisher publisher = new LoadAwareIndexerEventPublisher(
			loads,
			providers,
			event -> {
				delegated.add(event);
				return Future.succeededFuture();
			}
		);
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.withRole(IndexerRole.LOAD_WRITER)
			.build();

		loads.insert(new InsertIndexerLoad(
			20,
			10,
			null,
			LiveWriterPolicy.NONE,
			"history",
			IndexerLoadState.HISTORICAL_LOADING,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> publisher.publish(new IndexerEvent(
			IndexerEventType.ACTION_ITEM_FAILED,
			model,
			null,
			new IllegalStateException("document write failed")
		))).compose(ignored -> loads.getByIndexerId(20))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				IndexerLoadRecord load = found.orElseThrow();
				assertEquals(IndexerLoadState.FAILED, load.state());
				assertEquals("document write failed", load.failureReason());
				assertNotNull(load.failedAt());
				assertNotNull(provider.stopRequest);
				assertEquals(20, provider.stopRequest.indexerId());
				assertEquals("document write failed", provider.stopRequest.reason());
				assertEquals(1, delegated.size());
				testContext.completeNow();
			})));
	}

	@Test
	void delegatesEventWhenNoActiveLoadExists(VertxTestContext testContext) {
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryLoadProviderRegistry providers = new InMemoryLoadProviderRegistry();
		List<IndexerEvent> delegated = new ArrayList<>();
		LoadAwareIndexerEventPublisher publisher = new LoadAwareIndexerEventPublisher(
			loads,
			providers,
			event -> {
				delegated.add(event);
				return Future.succeededFuture();
			}
		);
		IndexerModel model = IndexerModel.builder()
			.withId(20)
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withQueueName("customers_1")
			.withRole(IndexerRole.LOAD_WRITER)
			.build();

		publisher.publish(new IndexerEvent(
			IndexerEventType.ACTION_ITEM_FAILED,
			model,
			null,
			new IllegalStateException("document write failed")
		)).onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
			assertEquals(1, delegated.size());
			testContext.completeNow();
		})));
	}

	private static class CapturingLoadProvider implements LoadProvider {
		private LoadStopRequest stopRequest;

		@Override
		public Future<Void> start(LoadRequest request, LoadWriter writer) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> stop(LoadStopRequest request) {
			this.stopRequest = request;
			return Future.succeededFuture();
		}
	}
}
