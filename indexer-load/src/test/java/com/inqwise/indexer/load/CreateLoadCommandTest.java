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
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.commands.CleanupDeletingIndexerCommandHandler;
import com.inqwise.indexer.commands.DeleteIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MetadataIndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

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
		InMemoryCommandEngine commands = commandService(metadata, loads, provider);
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
				assertEquals(LiveWriterPolicy.NONE, created.load().liveWriterPolicy());
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
	void createsLoadWithoutStartCommandWiringAndLeavesProviderUnstarted(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryCommandEngine commands = new InMemoryCommandEngine()
			.register(new CreateLoadCommandHandler(
				metadata,
				loads,
				new InMemoryIndexerLifecycleEventBus()
			));

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
		)).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id())))
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertEquals(IndexerLoadState.CREATED, found.orElseThrow().state());
				testContext.completeNow();
			})));
	}

	@Test
	void startingLoadCanRetryProviderStartUsingIndexerIdIdentity(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandEngine commands = createOnlyCommandService(metadata, loads)
			.register(new StartLoadCommandHandler(metadata, loads, queue, registry, eventBus));

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
		)).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id())))
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				return loads.updateState(new UpdateIndexerLoadState(
					load.indexerId(),
					IndexerLoadState.STARTING,
					load.version()
				)).compose(ignored -> commands.submit(new StartLoadCommand(
					load.indexerId(),
					0L
				))).compose(ignored -> loads.getByIndexerId(load.indexerId()));
			})
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				IndexerLoadRecord load = found.orElseThrow();
				assertEquals(IndexerLoadState.HISTORICAL_LOADING, load.state());
				assertEquals(1, provider.startCount);
				assertNotNull(provider.request);
				assertEquals(load.indexerId(), provider.request.indexerId());
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
		InMemoryCommandEngine commands = commandService(metadata, loads, registry);

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
		InMemoryCommandEngine commands = commandService(metadata, loads, provider);

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
				assertEquals(LiveWriterPolicy.CREATE_IMMEDIATELY, created.load().liveWriterPolicy());
				assertEquals(liveWriter.id(), provider.request.liveIndexerId());
				assertEquals("customers--queue-live", liveWriter.queueName());
				testContext.completeNow();
			})));
	}

	@Test
	void marksLoadFailedWhenProviderStartFails(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryCommandEngine commands = commandService(
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
		InMemoryCommandEngine commands = commandService(metadata, loads, registry);
		registerCleanupHandlers(commands, metadata, loads);
		commands.register(new CancelLoadCommandHandler(loads, registry, commands));

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
				)).map(load);
			})
			.compose(load -> loads.getByIndexerId(load.indexerId())
				.compose(found -> metadata.listIndexersByTargetId(load.targetId())
					.map(indexers -> new Created(indexers, load, found.isPresent()))))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(false, created.loadPresent());
				assertNull(defaultProvider.stopRequest);
				assertNotNull(provider.stopRequest);
				assertEquals(created.load().indexerId(), provider.stopRequest.indexerId());
				assertEquals("operator cancel", provider.stopRequest.reason());
				assertTrue(created.indexers().isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void cancelCreatedLoadSkipsProviderStopAndDeletesIndexers(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		InMemoryCommandEngine commands = createOnlyCommandService(metadata, loads);
		registerCleanupHandlers(commands, metadata, loads);
		commands.register(new CancelLoadCommandHandler(loads, registry, commands));

		commands.submit(new CreateLoadCommand(
			"load",
			"default",
			"customers",
			"customers--idx-load",
			"customers--queue-load",
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			"customers--queue-live",
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id())))
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				assertEquals(IndexerLoadState.CREATED, load.state());
				return commands.submit(new CancelLoadCommand(
					load.indexerId(),
					"operator cancel",
					load.version()
				)).map(load);
			})
			.compose(load -> loads.getByIndexerId(load.indexerId())
				.compose(found -> metadata.listIndexersByTargetId(load.targetId())
					.map(indexers -> new Created(indexers, load, found.isPresent()))))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(false, created.loadPresent());
				assertNull(provider.stopRequest);
				assertTrue(created.indexers().isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void cancelStartingLoadStopsProviderAndDeletesIndexers(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		InMemoryCommandEngine commands = createOnlyCommandService(metadata, loads);
		registerCleanupHandlers(commands, metadata, loads);
		commands.register(new CancelLoadCommandHandler(loads, registry, commands));

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
		)).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id())))
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				return loads.updateState(new UpdateIndexerLoadState(
					load.indexerId(),
					IndexerLoadState.STARTING,
					load.version()
				)).compose(ignored -> loads.getByIndexerId(load.indexerId()));
			})
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				return commands.submit(new CancelLoadCommand(
					load.indexerId(),
					"operator cancel",
					load.version()
				)).map(load);
			})
			.compose(load -> loads.getByIndexerId(load.indexerId())
				.compose(found -> metadata.listIndexersByTargetId(load.targetId())
					.map(indexers -> new Created(indexers, load, found.isPresent()))))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(false, created.loadPresent());
				assertNotNull(provider.stopRequest);
				assertEquals(created.load().indexerId(), provider.stopRequest.indexerId());
				assertEquals("operator cancel", provider.stopRequest.reason());
				assertTrue(created.indexers().isEmpty());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandEngine createOnlyCommandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads
	) {
		return new InMemoryCommandEngine()
			.register(new CreateLoadCommandHandler(
				metadata,
				loads,
				new InMemoryIndexerLifecycleEventBus()
			));
	}

	private void registerCleanupHandlers(
		InMemoryCommandEngine commands,
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads
	) {
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		commands
			.register(new CleanupDeletingIndexerCommandHandler(
				metadata,
				IndexerQueueResourceManager.NOOP,
				IndexerDocumentIndexResourceManager.NOOP
			))
			.register(new DeleteIndexerCommandHandler(
				new MetadataIndexerOperations(
					metadata,
					LoadTestMetadataChangeNotifiers.create(eventBus)
				),
				commands
			))
			.register(new CleanupLoadCommandHandler(metadata, loads, commands));
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		LoadProvider provider
	) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		InMemoryCommandEngine commands = new InMemoryCommandEngine();
		return commands
			.register(new StartLoadCommandHandler(metadata, loads, queue, registry, eventBus))
			.register(new CreateLoadCommandHandler(metadata, loads, eventBus, commands));
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		LoadProviderRegistry registry
	) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryCommandEngine commands = new InMemoryCommandEngine();
		return commands
			.register(new StartLoadCommandHandler(metadata, loads, queue, registry, eventBus))
			.register(new CreateLoadCommandHandler(metadata, loads, eventBus, commands));
	}

	private record Created(
		List<IndexerRecord> indexers,
		IndexerLoadRecord load,
		boolean loadPresent
	) {
		private Created(List<IndexerRecord> indexers, IndexerLoadRecord load) {
			this(indexers, load, true);
		}
	}

	private static class CapturingLoadProvider implements LoadProvider {
		private LoadRequest request;
		private LoadStopRequest stopRequest;
		private int startCount;

		@Override
		public Future<Void> start(LoadRequest request, LoadWriter writer) {
			startCount++;
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
