package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.commands.DeleteIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.MutationState;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class CreateLoadCommandTest {
	@Test
	void createsLoadWriterStoresSourceFieldsAndStartsProvider(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryCommandService commands = commandService(metadata, loads, provider);
		JsonObject sourceQuery = new JsonObject().put("segment", "vip");

		commands.submit(new CreateLoadCommand(
			"load",
			"default",
			"customers",
			"customers--idx-load",
			"customers--queue-load",
			LiveWriterPolicy.NONE,
			null,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			Instant.parse("2020-01-01T00:00:00Z"),
			Instant.parse("2026-01-01T00:00:00Z"),
			sourceQuery,
			"customers-history",
			false
		)).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> metadata.listIndexersByTargetId(targets.get(0).id())
				.compose(indexers -> loads.getActiveByTargetId(targets.get(0).id())
					.map(load -> new Created(indexers, load.orElseThrow())))))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(1, created.indexers().size());
				IndexerRecord indexer = created.indexers().get(0);
				assertEquals(IndexerRole.LOAD_WRITER, indexer.role());
				assertEquals(IndexResourceOwnership.OWNER, indexer.indexOwnership());
				assertEquals(indexer.id(), created.load().indexerId());
				assertEquals(IndexerLoadState.HISTORICAL_LOADING, created.load().state());
				assertEquals("vip", created.load().sourceQuery().getString("segment"));
				assertEquals("customers-history", created.load().sourcePlaybookId());
				assertNotNull(provider.request);
				assertEquals(indexer.id(), provider.request.indexerId());
				assertEquals(indexer.targetId(), provider.request.targetId());
				assertEquals(indexer.indexName(), provider.request.indexName());
				assertEquals(indexer.queueName(), provider.request.queueName());
				assertNull(provider.request.liveIndexerId());
				assertEquals("vip", provider.request.sourceQuery().getString("segment"));
				testContext.completeNow();
			})));
	}

	@Test
	void usesProviderRegistryAndStoresProviderId(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider defaultProvider = new CapturingLoadProvider();
		CapturingLoadProvider historyProvider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", defaultProvider)
			.register("history", historyProvider);
		InMemoryCommandService commands = commandService(metadata, loads, registry);

		commands.submit(new CreateLoadCommand(
			"load",
			"history",
			"customers",
			"customers--idx-load",
			"customers--queue-load",
			LiveWriterPolicy.NONE,
			null,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			"customers-history",
			false
		)).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id())))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				IndexerLoadRecord load = found.orElseThrow();
				assertEquals("history", load.providerId());
				assertNull(defaultProvider.request);
				assertNotNull(historyProvider.request);
				assertEquals("history", historyProvider.request.providerId());
				testContext.completeNow();
			})));
	}

	@Test
	void createsImmediateLiveWriterLinkedToLoad(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryCommandService commands = commandService(metadata, loads, provider);

		commands.submit(new CreateLoadCommand(
			"load",
			"default",
			"customers",
			"customers--idx-load",
			"customers--queue-load",
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			"customers--queue-live",
			Instant.parse("2026-06-05T10:00:00Z"),
			Instant.parse("2026-06-05T09:55:00Z"),
			null,
			null,
			null,
			null,
			true
		)).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> metadata.listIndexersByTargetId(targets.get(0).id())
				.compose(indexers -> loads.getActiveByTargetId(targets.get(0).id())
					.map(load -> new Created(indexers, load.orElseThrow())))))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(2, created.indexers().size());
				IndexerRecord loadWriter = created.indexers().stream()
					.filter(indexer -> indexer.role() == IndexerRole.LOAD_WRITER)
					.findFirst()
					.orElseThrow();
				IndexerRecord liveWriter = created.indexers().stream()
					.filter(indexer -> indexer.role() == IndexerRole.LIVE_WRITER)
					.findFirst()
					.orElseThrow();
				assertEquals(IndexResourceOwnership.OWNER, loadWriter.indexOwnership());
				assertEquals(IndexResourceOwnership.ATTACHED, liveWriter.indexOwnership());
				assertEquals(loadWriter.id(), created.load().indexerId());
				assertEquals(liveWriter.id(), created.load().liveIndexerId());
				assertEquals(liveWriter.id(), provider.request.liveIndexerId());
				assertEquals("customers--queue-live", liveWriter.queueName());
				testContext.completeNow();
			})));
	}

	@Test
	void marksLoadFailedWhenProviderStartFails(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryCommandService commands = commandService(
			metadata,
			loads,
			new FailingLoadProvider()
		);

		commands.submit(new CreateLoadCommand(
			"load",
			"default",
			"customers",
			"customers--idx-load",
			"customers--queue-load",
			LiveWriterPolicy.NONE,
			null,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> Future.failedFuture("Expected provider start failure"))
			.recover(error -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id()))
			.map(load -> {
				assertTrue(load.isEmpty());
				return null;
			})
			.compose(ignored -> loads.getByIndexerId(1))
			.map(load -> {
				assertEquals(IndexerLoadState.FAILED, load.orElseThrow().state());
				assertEquals("provider unavailable", load.orElseThrow().failureReason());
				return null;
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void cancelStopsProviderMarksCancelledAndDeletesCreatedIndexers(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider defaultProvider = new CapturingLoadProvider();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", defaultProvider)
			.register("history", provider);
		InMemoryCommandService commands = commandService(metadata, loads, registry);
		commands
			.register(new DeleteIndexerCommandHandler(metadata, new InMemoryIndexerLifecycleEventBus()))
			.register(new CancelLoadCommandHandler(metadata, loads, registry, commands));

		commands.submit(new CreateLoadCommand(
			"load",
			"history",
			"customers",
			"customers--idx-load",
			"customers--queue-load",
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			"customers--queue-live",
			Instant.parse("2026-06-05T10:00:00Z"),
			Instant.parse("2026-06-05T09:55:00Z"),
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> loads.getByIndexerId(provider.request.indexerId()))
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				return commands.submit(new CancelLoadCommand(
					load.indexerId(),
					"operator cancel",
					load.version()
				));
			})
			.compose(ignored -> loads.getByIndexerId(provider.request.indexerId()))
			.compose(found -> metadata.listIndexersByTargetId(provider.request.targetId())
				.map(indexers -> new Created(indexers, found.orElseThrow())))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(IndexerLoadState.CANCELLED, created.load().state());
				assertNull(defaultProvider.stopRequest);
				assertNotNull(provider.stopRequest);
				assertEquals(created.load().indexerId(), provider.stopRequest.indexerId());
				assertEquals("operator cancel", provider.stopRequest.reason());
				for (IndexerRecord indexer : created.indexers()) {
					assertEquals(MutationState.DELETING, indexer.mutationState());
					assertEquals(IndexerRuntimeState.NON_ACTIVE, indexer.runtimeState());
				}
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		LoadProvider provider
	) {
		return new InMemoryCommandService()
			.register(new CreateLoadCommandHandler(
				metadata,
				loads,
				new InMemoryIndexerQueue(),
				provider,
				new InMemoryIndexerLifecycleEventBus()
			));
	}

	private InMemoryCommandService commandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		LoadProviderRegistry registry
	) {
		return new InMemoryCommandService()
			.register(new CreateLoadCommandHandler(
				metadata,
				loads,
				new InMemoryIndexerQueue(),
				registry,
				new InMemoryIndexerLifecycleEventBus()
			));
	}

	private record Created(
		List<IndexerRecord> indexers,
		IndexerLoadRecord load
	) {
	}

	private static class CapturingLoadProvider implements LoadProvider {
		private LoadRequest request;
		private LoadStopRequest stopRequest;

		@Override
		public Future<Void> start(LoadRequest request, LoadWriter writer) {
			this.request = request;
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> stop(LoadStopRequest request) {
			this.stopRequest = request;
			return Future.succeededFuture();
		}
	}

	private static class FailingLoadProvider implements LoadProvider {
		@Override
		public Future<Void> start(LoadRequest request, LoadWriter writer) {
			return Future.failedFuture("provider unavailable");
		}

		@Override
		public Future<Void> stop(LoadStopRequest request) {
			return Future.succeededFuture();
		}
	}
}
