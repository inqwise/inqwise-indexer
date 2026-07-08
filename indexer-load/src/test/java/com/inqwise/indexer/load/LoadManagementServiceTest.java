package com.inqwise.indexer.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.InMemoryIndexerQueue;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.commands.CleanupDeletingIndexerCommandHandler;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.DeleteIndexerCommandHandler;
import com.inqwise.indexer.commands.InMemoryCommandEngine;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MetadataIndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadManagementServiceTest {
	@Test
	void createsLoadWriterStoresSourceFieldsAndStartsProviderAfterExplicitStart(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		LoadManagementService service = new MetadataLoadManagementService(
			metadata,
			loads,
			new InMemoryIndexerQueue(),
			registry,
			eventBus,
			command -> Future.succeededFuture()
		);
		JsonObject sourceQuery = new JsonObject().put("segment", "vip");

		createReadyTarget(metadata).compose(targetId -> service.create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.NONE,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			Instant.parse("2020-01-01T00:00:00Z"),
			Instant.parse("2026-01-01T00:00:00Z"),
			sourceQuery,
			"customers-history",
			false
		))).compose(createdLoad -> {
			assertEquals(IndexerLoadState.CREATED, createdLoad.state());
			assertNull(provider.request);
			return service.start(new StartLoadRequest(
				createdLoad.indexerId(),
				createdLoad.version()
			));
		}).compose(startedLoad -> metadata.listTargets(null)
			.compose(targets -> metadata.listIndexersByTargetId(targets.get(0).id())
				.compose(indexers -> loads.getActiveByTargetId(targets.get(0).id())
					.map(load -> new Created(indexers, load.orElseThrow())))))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(1, created.indexers().size());
				IndexerRecord indexer = created.indexers().get(0);
				assertEquals(IndexerRole.LOAD_WRITER, indexer.role());
				assertEquals(IndexResourceOwnership.OWNER, indexer.indexOwnership());
				assertTrue(indexer.indexName().matches("customers--idx-[a-f0-9-]{36}"));
				assertTrue(indexer.queueName().matches("customers--queue-[a-f0-9-]{36}"));
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
	void startRetryAfterProviderAcceptanceReturnsExistingHistoricalLoading(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		LoadManagementService service = new MetadataLoadManagementService(
			metadata,
			loads,
			new InMemoryIndexerQueue(),
			registry,
			new InMemoryIndexerLifecycleEventBus(),
			command -> Future.succeededFuture()
		);

		createReadyTarget(metadata).compose(targetId -> service.create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.NONE,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		))).compose(created -> service.start(new StartLoadRequest(
			created.indexerId(),
			created.version()
		)).compose(started -> service.start(new StartLoadRequest(
			created.indexerId(),
			created.version()
		))))
			.onComplete(testContext.succeeding(retried -> testContext.verify(() -> {
				assertEquals(IndexerLoadState.HISTORICAL_LOADING, retried.state());
				assertEquals(1, provider.startCount);
				testContext.completeNow();
			})));
	}

	@Test
	void recoversPersistedCreatedLoadByStartingIt(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		CapturingLoadProvider provider = new CapturingLoadProvider();
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		LoadManagementService service = new MetadataLoadManagementService(
			metadata, loads, new InMemoryIndexerQueue(), registry, eventBus,
			command -> Future.succeededFuture()
		);
		createReadyTarget(metadata).compose(targetId -> service.create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.NONE,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		)))
			.compose(created -> service.recoverCreated(new RecoverCreatedLoadRequest(
				created.indexerId(), created.version()
			)))
			.compose(recovered -> metadata.listIndexersByTargetId(recovered.targetId())
				.map(indexers -> new Created(indexers, recovered)))
			.onComplete(testContext.succeeding(created -> testContext.verify(() -> {
				assertEquals(IndexerLoadState.HISTORICAL_LOADING, created.load().state());
				assertEquals(1, created.indexers().size());
				assertEquals(1, provider.startCount);
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsLoadCreationWhenTargetDoesNotExist(VertxTestContext testContext) {
		LoadManagementService service = new MetadataLoadManagementService(
			new InMemoryDocumentStoreMetadataRepository(),
			new InMemoryIndexerLoadRepository(),
			new InMemoryIndexerQueue(),
			new InMemoryLoadProviderRegistry(),
			new InMemoryIndexerLifecycleEventBus(),
			command -> Future.succeededFuture()
		);

		service.create(new CreateLoadRequest(
			"default",
			999,
			LiveWriterPolicy.NONE,
			null,
			null,
			null,
			null,
			null,
			null,
			false
		)).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertTrue(error.getMessage().contains("Target not found"));
			testContext.completeNow();
		})));
	}

	@Test
	void rejectsLoadCreationWhenTargetIsNotReady(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		LoadManagementService service = new MetadataLoadManagementService(
			metadata,
			new InMemoryIndexerLoadRepository(),
			new InMemoryIndexerQueue(),
			new InMemoryLoadProviderRegistry(),
			new InMemoryIndexerLifecycleEventBus(),
			command -> Future.succeededFuture()
		);

		createTarget(metadata, TargetStatus.ACTIVE, TargetProvisioningState.PROVISIONING)
			.compose(ignored -> service.create(new CreateLoadRequest(
				"default",
				1,
				LiveWriterPolicy.NONE,
				null,
				null,
				null,
				null,
				null,
				null,
				false
			))).onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("not ready"));
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsCreatedLoadRecoveryAtStaleVersion(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger submissions = new AtomicInteger();
		LoadManagementService createService = new MetadataLoadManagementService(
			metadata, loads, new InMemoryIndexerQueue(), new InMemoryLoadProviderRegistry(), eventBus,
			command -> Future.succeededFuture()
		);

		createReadyTarget(metadata).compose(targetId -> createService.create(new CreateLoadRequest(
			"default", targetId, LiveWriterPolicy.NONE, null, null, null, null, null, null, false
		))).compose(created -> new MetadataLoadManagementService(
			metadata,
			loads,
			new InMemoryIndexerQueue(),
			new InMemoryLoadProviderRegistry(),
			eventBus,
			command -> {
				submissions.incrementAndGet();
				return Future.succeededFuture();
			}
		).recoverCreated(new RecoverCreatedLoadRequest(
			created.indexerId(), created.version() + 1
		))).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertEquals(0, submissions.get());
			assertTrue(error.getMessage().contains("version conflict"));
			testContext.completeNow();
		})));
	}

	@Test
	void approvalRetryResubmitsPublishOnlyForExactResult(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		AtomicInteger submissions = new AtomicInteger();
		LoadManagementService service = new MetadataLoadManagementService(
			metadata,
			loads,
			new InMemoryIndexerQueue(),
			new InMemoryLoadProviderRegistry(),
			new InMemoryIndexerLifecycleEventBus(),
			command -> {
				assertEquals(PublishLoadCommand.TYPE, command.getType());
				submissions.incrementAndGet();
				return Future.succeededFuture();
			}
		);
		Instant approvedAt = Instant.parse("2026-06-05T11:00:00Z");
		ApproveLoadPublicationRequest approval = new ApproveLoadPublicationRequest(
			41, approvedAt, "reviewer", "checked", 0L
		);

		loads.insert(new InsertIndexerLoad(
			41,
			17,
			null,
			LiveWriterPolicy.NONE,
			"default",
			IndexerLoadState.WAITING_FOR_REVIEW,
			null,
			null,
			null,
			null,
			null,
			null,
			true
		)).compose(ignored -> service.approvePublication(approval))
			.compose(first -> {
				assertEquals(IndexerLoadState.APPROVED, first.state());
				assertEquals(1L, first.version());
				return service.approvePublication(approval);
			})
			.compose(ignored -> service.approvePublication(new ApproveLoadPublicationRequest(
				41, approvedAt, "reviewer", "changed", 0L
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals(2, submissions.get());
				assertTrue(error.getMessage().contains("version conflict"));
				testContext.completeNow();
			})));
	}

	@Test
	void cancellationRetryResubmitsCleanupOnlyForExactResult(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		AtomicInteger submissions = new AtomicInteger();
		LoadManagementService service = new MetadataLoadManagementService(
			metadata,
			loads,
			new InMemoryIndexerQueue(),
			new InMemoryLoadProviderRegistry(),
			new InMemoryIndexerLifecycleEventBus(),
			command -> {
				assertEquals(CleanupLoadCommand.TYPE, command.getType());
				submissions.incrementAndGet();
				return Future.succeededFuture();
			}
		);
		CancelLoadRequest cancellation = new CancelLoadRequest(43, "operator cancel", 0L);

		loads.insert(new InsertIndexerLoad(
			43,
			19,
			null,
			LiveWriterPolicy.NONE,
			"default",
			IndexerLoadState.CREATED,
			null,
			null,
			null,
			null,
			null,
			null,
			false
		)).compose(ignored -> service.cancel(cancellation))
			.compose(ignored -> loads.getByIndexerId(43))
			.compose(found -> {
				IndexerLoadRecord cancelled = found.orElseThrow();
				assertEquals(IndexerLoadState.CANCELLED, cancelled.state());
				assertEquals(1L, cancelled.version());
				return service.cancel(cancellation);
			})
			.compose(ignored -> service.cancel(new CancelLoadRequest(
				43, "operator cancel", 1L
			)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals(2, submissions.get());
				assertTrue(error.getMessage().contains("not cancellable"));
				testContext.completeNow();
			})));
	}

	@Test
	void cancellationDoesNotTreatUnknownLoadAsCompleted(VertxTestContext testContext) {
		LoadManagementService service = new MetadataLoadManagementService(
			new InMemoryDocumentStoreMetadataRepository(),
			new InMemoryIndexerLoadRepository(),
			new InMemoryIndexerQueue(),
			new InMemoryLoadProviderRegistry(),
			new InMemoryIndexerLifecycleEventBus(),
			command -> Future.succeededFuture()
		);

		service.cancel(new CancelLoadRequest(999, "unknown", 0L))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("not found"));
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
		LoadManagementService service = new MetadataLoadManagementService(
			metadata,
			loads,
			queue,
			registry,
			new InMemoryIndexerLifecycleEventBus(),
			command -> Future.succeededFuture()
		);

		createReadyTarget(metadata).compose(targetId -> loadService(
			metadata,
			loads,
			command -> Future.succeededFuture()
		).create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.NONE,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		))).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id())))
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				return loads.updateState(new UpdateIndexerLoadState(
					load.indexerId(),
					IndexerLoadState.STARTING,
					load.version()
				)).compose(ignored -> service.start(new StartLoadRequest(
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

		LoadManagementService service = loadService(metadata, loads, registry, commands);

		createReadyTarget(metadata).compose(targetId -> service.create(new CreateLoadRequest(
			"history",
			targetId,
			LiveWriterPolicy.NONE,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			"customers-history",
			false
		))).compose(created -> service.start(new StartLoadRequest(
			created.indexerId(),
			created.version()
		))).compose(ignored -> metadata.listTargets(null)
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
		InMemoryLoadProviderRegistry registry = new InMemoryLoadProviderRegistry()
			.register("default", provider);
		InMemoryCommandEngine commands = commandService(metadata, loads, provider);

		LoadManagementService service = loadService(metadata, loads, registry, commands);

		createReadyTarget(metadata).compose(targetId -> service.create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			Instant.parse("2026-06-05T10:00:00Z"),
			Instant.parse("2026-06-05T09:55:00Z"),
			null,
			null,
			null,
			null,
			true
		))).compose(created -> service.start(new StartLoadRequest(
			created.indexerId(),
			created.version()
		))).compose(ignored -> metadata.listTargets(null)
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
				assertEquals(loadWriter.indexName(), liveWriter.indexName());
				assertEquals(loadWriter.queueName() + "--live", liveWriter.queueName());
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

		LoadManagementService service = loadService(
			metadata,
			loads,
			new InMemoryLoadProviderRegistry().register("default", new FailingLoadProvider()),
			commands
		);

		createReadyTarget(metadata).compose(targetId -> service.create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.NONE,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		))).compose(created -> service.start(new StartLoadRequest(
			created.indexerId(),
			created.version()
		)))
			.compose(ignored -> Future.failedFuture("Expected provider start failure"))
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
		LoadManagementService service = loadService(metadata, loads, registry, commands);

		createReadyTarget(metadata).compose(targetId -> service.create(new CreateLoadRequest(
			"history",
			targetId,
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			Instant.parse("2026-06-05T10:00:00Z"),
			Instant.parse("2026-06-05T09:55:00Z"),
			null,
			null,
			null,
			null,
			false
		))).compose(created -> service.start(new StartLoadRequest(
			created.indexerId(),
			created.version()
		))).compose(ignored -> loads.getByIndexerId(provider.request.indexerId()))
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				return service.cancel(new CancelLoadRequest(
					load.indexerId(),
					"operator cancel",
					load.version()
				)).map(load);
			})
			.compose(load -> loads.getByIndexerId(load.indexerId())
				.compose(found -> metadata.listIndexersByTargetId(load.targetId())
					.map(indexers -> new Created(indexers, load, found.isPresent()))))
			.compose(created -> service.cancel(new CancelLoadRequest(
				created.load().indexerId(),
				"retry after cleanup",
				created.load().version()
			)).map(created))
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
		InMemoryCommandEngine commands = new InMemoryCommandEngine();
		registerCleanupHandlers(commands, metadata, loads);
		LoadManagementService cancelService = loadService(metadata, loads, registry, commands);

		createReadyTarget(metadata).compose(targetId -> loadService(
			metadata,
			loads,
			command -> Future.succeededFuture()
		).create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.CREATE_IMMEDIATELY,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		))).compose(ignored -> metadata.listTargets(null)
			.compose(targets -> loads.getActiveByTargetId(targets.get(0).id())))
			.compose(found -> {
				IndexerLoadRecord load = found.orElseThrow();
				assertEquals(IndexerLoadState.CREATED, load.state());
				return cancelService.cancel(new CancelLoadRequest(
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
		InMemoryCommandEngine commands = new InMemoryCommandEngine();
		registerCleanupHandlers(commands, metadata, loads);
		LoadManagementService cancelService = loadService(metadata, loads, registry, commands);

		createReadyTarget(metadata).compose(targetId -> loadService(
			metadata,
			loads,
			command -> Future.succeededFuture()
		).create(new CreateLoadRequest(
			"default",
			targetId,
			LiveWriterPolicy.NONE,
			Instant.parse("2026-06-05T10:00:00Z"),
			null,
			null,
			null,
			null,
			null,
			false
		))).compose(ignored -> metadata.listTargets(null)
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
				return cancelService.cancel(new CancelLoadRequest(
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

	private LoadManagementService loadService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		CommandService commandService
	) {
		return loadService(
			metadata, loads, new InMemoryLoadProviderRegistry(), commandService
		);
	}

	private LoadManagementService loadService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		LoadProviderRegistry loadProviderRegistry,
		CommandService commandService
	) {
		return new MetadataLoadManagementService(
			metadata,
			loads,
			new InMemoryIndexerQueue(),
			loadProviderRegistry,
			new InMemoryIndexerLifecycleEventBus(),
			commandService
		);
	}

	private Future<Integer> createReadyTarget(InMemoryDocumentStoreMetadataRepository metadata) {
		return createTarget(metadata, TargetStatus.ACTIVE, TargetProvisioningState.READY);
	}

	private Future<Integer> createTarget(
		InMemoryDocumentStoreMetadataRepository metadata,
		TargetStatus status,
		TargetProvisioningState provisioningState
	) {
		return metadata.insertTarget(new InsertTarget(
			"target",
			"customers",
			null,
			null,
			null,
			status,
			provisioningState
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
		return new InMemoryCommandEngine();
	}

	private InMemoryCommandEngine commandService(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		LoadProviderRegistry registry
	) {
		return new InMemoryCommandEngine();
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
