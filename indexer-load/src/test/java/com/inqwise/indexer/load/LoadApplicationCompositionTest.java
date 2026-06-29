package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import com.inqwise.coordination.LocalExclusiveFlowCoordinator;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.IndexerEventPublisher;
import com.inqwise.indexer.IndexerOptions;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntime;
import com.inqwise.indexer.IndexerRuntimeReconciler;
import com.inqwise.indexer.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.providers.IndexerPlugins;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadApplicationCompositionTest {
	@Test
	void composedHistoricalLoadRunsThroughPublicationAndCleanup(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository metadata =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerDocumentStore documentStore = new InMemoryIndexerDocumentStore();
		InMemoryIndexerLifecycleEventBus lifecycleEvents = new InMemoryIndexerLifecycleEventBus();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry providers = new InMemoryLoadProviderRegistry()
			.register("history", provider);
		InMemoryCommandEngine commands = new InMemoryCommandEngine();

		LoadCommandHandlers.register(commands, new LoadCommandHandlers.Config(
			metadata,
			loads,
			queue,
			providers,
			lifecycleEvents
		));
		LoadIndexerPlugin loadPlugin = new LoadIndexerPlugin(
			metadata,
			loads,
			commands,
			EventPublisher.NOOP,
			new LocalExclusiveFlowCoordinator(),
			lifecycleEvents
		);
		IndexerRuntime runtime = new IndexerRuntime(
			vertx,
			queue,
			documentStore,
			new IndexerOptions(),
			IndexerEventPublisher.NOOP,
			new IndexerPlugins(List.of(loadPlugin))
		);
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			metadata,
			lifecycleEvents,
			runtime
		);
		CreateLoadCommand create = new CreateLoadCommand(
			"load",
			"history",
			"customers",
			"customers--history",
			"customers--load",
			LiveWriterPolicy.NONE,
			null,
			Instant.parse("2026-06-22T06:00:00Z"),
			null,
			null,
			Instant.parse("2026-06-22T06:00:00Z"),
			new JsonObject().put("segment", "all"),
			"customers-history",
			false
		);

		commands.submit(create)
			.compose(ignored -> reconciler.reconcile(provider.request.indexerId()))
			.compose(ignored -> provider.writer.complete(new LoadCompletion(null)))
			.compose(ignored -> awaitPublishedAndCleaned(
				vertx,
				metadata,
				loads,
				provider.request.indexerId(),
				100
			))
			.eventually(runtime::stop)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("customers", provider.request.targetName());
				assertEquals("customers-history", provider.request.sourcePlaybookId());
				testContext.completeNow();
			})));
	}

	private Future<Void> awaitPublishedAndCleaned(
		Vertx vertx,
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		Integer indexerId,
		int attempts
	) {
		return metadata.getIndexerById(indexerId)
			.compose(indexer -> loads.getByIndexerId(indexerId)
				.compose(load -> {
					boolean complete = indexer.isPresent()
						&& indexer.get().role() == IndexerRole.LIVE_WRITER
						&& indexer.get().publicationState() == PublicationState.PUBLISHED
						&& load.isEmpty();
					if (complete) {
						return Future.succeededFuture();
					}
					if (attempts == 0) {
						return Future.failedFuture("Load workflow did not complete");
					}
					return Future.<Void>future(promise -> vertx.setTimer(
						10,
						ignored -> promise.complete()
					)).compose(ignored -> awaitPublishedAndCleaned(
						vertx,
						metadata,
						loads,
						indexerId,
						attempts - 1
					));
				}));
	}

	private static final class CapturingLoadProvider implements LoadProvider {
		private LoadRequest request;
		private LoadWriter writer;

		@Override
		public Future<Void> start(LoadRequest request, LoadWriter writer) {
			this.request = request;
			this.writer = writer;
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> stop(LoadStopRequest request) {
			return Future.succeededFuture();
		}
	}
}
