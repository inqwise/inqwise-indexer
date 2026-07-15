package com.inqwise.indexer.load.runtime;

import com.inqwise.indexer.load.adapters.local.InMemoryIndexerLoadRepository;
import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.api.LoadWriter;
import com.inqwise.indexer.load.adapters.metadata.MetadataLazyLiveWriterCatalog;
import com.inqwise.indexer.load.events.LazyLiveWriterPreparationConflictEvent;
import com.inqwise.indexer.load.events.LazyLiveWriterPreparationConflictReason;
import com.inqwise.indexer.load.repository.AttachLiveWriterRequest;
import com.inqwise.indexer.load.repository.AttachLiveWriterResult;
import com.inqwise.indexer.load.repository.InsertIndexerLoad;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadState;
import com.inqwise.indexer.load.testing.LoadTestMetadataChangeNotifiers;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.events.RecordingEventPublisher;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerLifecycleProviderSignal;
import com.inqwise.indexer.lifecycle.IndexerLifecycleSubscription;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.runtime.IndexerQueueClient;
import com.inqwise.indexer.runtime.IndexerQueueConsumer;
import com.inqwise.indexer.runtime.IndexerQueueConsumerOptions;
import com.inqwise.indexer.runtime.IndexerQueuePublisher;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.metadata.MetadataIndexerModels;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.cleanup.DeleteIndexerCommand;
import com.inqwise.indexer.adapters.local.InMemoryCommandEngine;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.routing.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.adapters.local.StaticTargetDefinitionProvider;
import com.inqwise.indexer.adapters.local.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.providers.ActionReceiveReadiness;
import com.inqwise.indexer.providers.IndexerPlugins;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.provisioning.MetadataIndexerProvisioningService;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadWriterActionReceiveCapabilityTest {
	@Test
	void coldSubmitPreparesLazyLoadWriterAndPublishesToLinkedLiveWriter(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		IndexerPlugins plugins = new IndexerPlugins(List.of(new LoadIndexerPlugin(
			new MetadataLazyLiveWriterCatalog(metadata),
			loads,
			command -> Future.succeededFuture(),
			EventPublisher.NOOP
		)));
		RecordingEventBus eventBus = new RecordingEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandEngine commands = new InMemoryCommandEngine()
			.register(new SubmitIndexActionsCommandHandler(
				metadata,
				new StaticTargetDefinitionProvider(List.of()),
				new MetadataIndexerProvisioningService(
					metadata,
					new StaticIndexerDefinitionProvider(new IndexerDefinition(
						new IndexDefinition("default", "1", null, null),
						new QueueDefinition(null)
					)),
					IndexerDocumentIndexResourceManager.NOOP,
					IndexerQueueResourceManager.NOOP
				),
				LoadTestMetadataChangeNotifiers.create(eventBus),
				queue,
				null,
				plugins
			));

		insertLazyLoad(metadata, loads)
			.compose(load -> commands.submit(new SubmitIndexActionsCommand(List.of(
				targetedPut(load.targetId(), "42", "Ada")
			))).compose(ignored -> loads.getByIndexerId(load.indexerId()))
				.compose(updated -> metadata.getIndexerById(updated.orElseThrow().liveIndexerId())
					.map(liveWriter -> new Result(updated.orElseThrow(), liveWriter.orElseThrow()))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(1L, result.load().version());
				assertEquals(IndexerRole.LIVE_WRITER, result.liveWriter().role());
				assertEquals(IndexResourceOwnership.ATTACHED, result.liveWriter().indexOwnership());
				assertEquals("customers--idx-load", result.liveWriter().indexName());
				assertEquals("customers--queue-load--live", result.liveWriter().queueName());
				assertEquals(1, eventBus.events.size());
				assertEquals(result.liveWriter().id(), eventBus.events.get(0).getIndexerId());
				assertEquals(1, queue.publishedByQueueName.get(result.liveWriter().queueName()).size());
				assertConcretePut(
					queue.publishedByQueueName.get(result.liveWriter().queueName()).get(0),
					result.liveWriter()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void loadWriterReadinessIsNoAfterLiveWriterLinked(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLoadRepository loads = new InMemoryIndexerLoadRepository();
		LoadWriterActionReceiveCapability capability =
			capability(metadata, loads);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.canReceive(
					MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
					targetedPut(load.targetId(), "42", "Ada")
				).compose(readiness -> {
					assertEquals(ActionReceiveReadiness.REQUIRES_PREPARE, readiness);
					return capability.prepareToReceive(new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
						"command-1",
						MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
						List.of(targetedPut(load.targetId(), "42", "Ada")),
						null
					)).compose(ignored -> capability.canReceive(
						MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
						targetedPut(load.targetId(), "43", "Grace")
					));
				})))
			.onComplete(testContext.succeeding(readiness -> testContext.verify(() -> {
				assertEquals(ActionReceiveReadiness.NO, readiness);
				testContext.completeNow();
			})));
	}

	@Test
	void concurrentPreparationSharesOneLocalFlow(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		BlockingAttachLoadRepository loads = new BlockingAttachLoadRepository();
		LoadWriterActionReceiveCapability capability =
			capability(metadata, loads);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> {
					com.inqwise.indexer.providers.PrepareIndexerForActionsRequest firstRequest =
						new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
							"command-1",
							MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
							List.of(),
							null
						);
					com.inqwise.indexer.providers.PrepareIndexerForActionsRequest secondRequest =
						new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
							"command-2",
							MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
							List.of(),
							null
						);

					Future<com.inqwise.indexer.providers.PreparedIndexers> first =
						capability.prepareToReceive(firstRequest);
					Future<com.inqwise.indexer.providers.PreparedIndexers> second =
						capability.prepareToReceive(secondRequest);
					assertEquals(1, loads.attachCalls);
					loads.completeAttach();
					return Future.all(first, second);
				}))
			.onComplete(testContext.succeeding(results -> testContext.verify(() -> {
				com.inqwise.indexer.providers.PreparedIndexers first = results.resultAt(0);
				com.inqwise.indexer.providers.PreparedIndexers second = results.resultAt(1);
				assertEquals(first.indexers().get(0).getId(), second.indexers().get(0).getId());
				assertEquals(first, second);
				assertEquals(1, loads.attachCalls);
				testContext.completeNow();
			})));
	}

	@Test
	void prepareRetriesOnceAfterVersionConflict(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		OneTimeVersionConflictLoadRepository loads = new OneTimeVersionConflictLoadRepository();
		List<Command> submittedCommands = new ArrayList<>();
		CommandService commands = command -> {
			submittedCommands.add(command);
			return Future.succeededFuture();
		};
		RecordingEventPublisher events = new RecordingEventPublisher();
		LoadWriterActionReceiveCapability capability =
			capability(metadata, loads, commands, events);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.prepareToReceive(new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
					"command-1",
					MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
					List.of(targetedPut(load.targetId(), "42", "Ada")),
					null
				)).compose(prepared -> loads.getByIndexerId(load.indexerId())
					.map(updated -> new PrepareResult(prepared, updated.orElseThrow())))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(1, result.prepared().indexers().size());
				assertEquals(IndexerRole.LIVE_WRITER, result.prepared().indexers().get(0).getRole());
				assertEquals(result.prepared().indexers().get(0).getId(), result.load().liveIndexerId());
				assertEquals(2L, result.load().version());
				assertEquals(1, submittedCommands.size());
				DeleteIndexerCommand cleanup = assertInstanceOf(
					DeleteIndexerCommand.class,
					submittedCommands.get(0)
				);
				assertEquals(0L, cleanup.getExpectedVersion());
				assertEquals(1, events.publishedEvents().size());
				LazyLiveWriterPreparationConflictEvent event = assertInstanceOf(
					LazyLiveWriterPreparationConflictEvent.class,
					events.publishedEvents().get(0).event().payload()
				);
				assertEquals(LazyLiveWriterPreparationConflictReason.VERSION_CONFLICT, event.reason());
				assertEquals(cleanup.getIndexerId(), event.candidateLiveIndexerId());
				assertTrue(event.cleanupSubmitted());
				assertNull(event.cleanupSucceeded());
				assertEquals("command-1", events.publishedEvents().get(0).event().correlationId());
				testContext.completeNow();
			})));
	}

	@Test
	void attachLoserReturnsWinnerAndSubmitsCandidateCleanup(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		WinnerAttachLoadRepository loads = new WinnerAttachLoadRepository();
		List<Command> submittedCommands = new ArrayList<>();
		RecordingEventPublisher events = new RecordingEventPublisher();
		LoadWriterActionReceiveCapability capability = capability(
			metadata,
			loads,
			command -> {
				submittedCommands.add(command);
				return Future.succeededFuture();
			},
			events
		);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> metadata.insertIndexer(new InsertIndexer(
					"winner",
					load.targetId(),
					loadWriter.orElseThrow().targetName(),
					loadWriter.orElseThrow().indexName(),
					"customers--queue-winner",
					IndexerType.INDEX,
					IndexerRole.LIVE_WRITER,
					IndexResourceOwnership.ATTACHED,
					IndexerRuntimeState.ACTIVE,
					PublicationState.UNPUBLISHED,
					MutationState.WRITABLE
				)).compose(winnerId -> {
					loads.winnerId = winnerId;
					return capability.prepareToReceive(
						new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
							"command-2",
							MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
							List.of(),
							null
						)
					).map(prepared -> new WinnerResult(prepared, winnerId));
				})))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(result.winnerId(), result.prepared().indexers().get(0).getId());
				assertEquals(false, result.prepared().metadataChanged());
				assertEquals(1, submittedCommands.size());
				LazyLiveWriterPreparationConflictEvent event = assertInstanceOf(
					LazyLiveWriterPreparationConflictEvent.class,
					events.publishedEvents().get(0).event().payload()
				);
				assertEquals(LazyLiveWriterPreparationConflictReason.ATTACH_LOST, event.reason());
				assertEquals(result.winnerId(), event.winnerLiveIndexerId());
				assertTrue(event.cleanupSubmitted());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanupSubmissionFailureIsVisibleWithoutBlockingStaleRetry(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		OneTimeVersionConflictLoadRepository loads = new OneTimeVersionConflictLoadRepository();
		RecordingEventPublisher events = new RecordingEventPublisher();
		LoadWriterActionReceiveCapability capability = capability(
			metadata,
			loads,
			command -> Future.failedFuture("cleanup transport unavailable"),
			events
		);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.prepareToReceive(
					new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
						"command-3",
						MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
						List.of(),
						null
					)
				)))
			.onComplete(testContext.succeeding(prepared -> testContext.verify(() -> {
				assertEquals(1, prepared.indexers().size());
				LazyLiveWriterPreparationConflictEvent event = assertInstanceOf(
					LazyLiveWriterPreparationConflictEvent.class,
					events.publishedEvents().get(0).event().payload()
				);
				assertEquals(LazyLiveWriterPreparationConflictReason.CLEANUP_FAILED, event.reason());
				assertEquals(false, event.cleanupSubmitted());
				assertEquals(false, event.cleanupSucceeded());
				testContext.completeNow();
			})));
	}

	@Test
	void prepareFailsWithRetryableStaleStateAfterSecondVersionConflict(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository metadata = new InMemoryDocumentStoreMetadataRepository();
		AlwaysVersionConflictLoadRepository loads = new AlwaysVersionConflictLoadRepository();
		LoadWriterActionReceiveCapability capability =
			capability(metadata, loads);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.prepareToReceive(new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
					"command-1",
					MetadataIndexerModels.fromRecord(loadWriter.orElseThrow()),
					List.of(targetedPut(load.targetId(), "42", "Ada")),
					null
				))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertInstanceOf(RetryableStaleStateException.class, error);
				assertEquals("Indexer load changed while preparing live writer: 1", error.getMessage());
				testContext.completeNow();
			})));
	}

	private Future<IndexerLoadRecord> insertLazyLoad(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads
	) {
		return metadata.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> metadata.insertIndexer(new InsertIndexer(
				"load",
				targetId,
				"customers",
				"customers--idx-load",
				"customers--queue-load",
				IndexerType.INDEX,
				IndexerRole.LOAD_WRITER,
				IndexResourceOwnership.OWNER,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> loads.insert(new InsertIndexerLoad(
				indexerId,
				targetId,
				null,
				LiveWriterPolicy.CREATE_ON_FIRST_LIVE_ACTION,
				"default",
				IndexerLoadState.HISTORICAL_LOADING,
				Instant.parse("2026-06-05T10:00:00Z"),
				null,
				null,
				null,
				null,
				null,
				false
			)).compose(ignored -> loads.getByIndexerId(indexerId))
				.map(found -> found.orElseThrow())));
	}

	private LoadWriterActionReceiveCapability capability(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads
	) {
		return new LoadWriterActionReceiveCapability(
			new MetadataLazyLiveWriterCatalog(metadata),
			loads,
			null,
			EventPublisher.NOOP,
			new com.inqwise.coordination.LocalExclusiveFlowCoordinator()
		);
	}

	private LoadWriterActionReceiveCapability capability(
		InMemoryDocumentStoreMetadataRepository metadata,
		InMemoryIndexerLoadRepository loads,
		CommandService commands,
		EventPublisher events
	) {
		return new LoadWriterActionReceiveCapability(
			new MetadataLazyLiveWriterCatalog(metadata),
			loads,
			commands,
			events,
			new com.inqwise.coordination.LocalExclusiveFlowCoordinator()
		);
	}

	private void assertConcretePut(IndexerActionItem item, IndexerRecord liveWriter) {
		PutDocumentActionItem put = (PutDocumentActionItem) item;
		assertEquals(liveWriter.targetId(), put.getTargetId());
		assertEquals(liveWriter.id(), put.getIndexerId());
		assertEquals(liveWriter.indexName(), put.getIndexName());
		assertEquals("42", put.getUid());
		assertEquals("Ada", put.getDocument().getString("name"));
	}

	private PutDocumentActionItem targetedPut(Integer targetId, String uid, String name) {
		return new PutDocumentActionItem(new JsonObject()
			.put(PutDocumentActionItem.TARGET_ID, targetId)
			.put(PutDocumentActionItem.UID, uid)
			.put(PutDocumentActionItem.DOCUMENT, new JsonObject().put("name", name)));
	}

	private static class RecordingEventBus implements IndexerLifecycleEventBus {
		private final List<IndexerMetadataChanged> events = new ArrayList<>();

		@Override
		public Future<Void> publish(IndexerMetadataChanged event) {
			events.add(event);
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> publish(TargetMetadataChanged event) {
			return Future.succeededFuture();
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribe(
			Handler<IndexerMetadataChanged> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribeTarget(
			Handler<TargetMetadataChanged> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribeProviderSignals(
			Handler<IndexerLifecycleProviderSignal> handler
		) {
			return Future.succeededFuture(IndexerLifecycleSubscription.NOOP);
		}
	}

	private static class RecordingQueue implements IndexerQueueClient {
		private final Map<String, List<IndexerActionItem>> publishedByQueueName =
			new LinkedHashMap<>();

		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.succeededFuture(new IndexerQueuePublisher() {
				@Override
				public Future<Void> publish(IndexerActionItem item) {
					publishedByQueueName
						.computeIfAbsent(queueName, ignored -> new ArrayList<>())
						.add(item);
					return Future.succeededFuture();
				}

				@Override
				public Future<Void> close() {
					return Future.succeededFuture();
				}
			});
		}

		@Override
		public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
			return Future.failedFuture("consumer is not expected");
		}
	}

	private record Result(
		IndexerLoadRecord load,
		IndexerRecord liveWriter
	) {
	}

	private record PrepareResult(
		com.inqwise.indexer.providers.PreparedIndexers prepared,
		IndexerLoadRecord load
	) {
	}

	private record WinnerResult(
		com.inqwise.indexer.providers.PreparedIndexers prepared,
		Integer winnerId
	) {
	}

	private static class OneTimeVersionConflictLoadRepository extends InMemoryIndexerLoadRepository {
		private boolean conflictInjected;

		@Override
		public synchronized Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(
			AttachLiveWriterRequest request
		) {
			if (!conflictInjected) {
				conflictInjected = true;
				Future<Void> updated = updateState(new UpdateIndexerLoadState(
					request.indexerId(),
					IndexerLoadState.HISTORICAL_LOADING,
					request.expectedVersion()
				));
				if (updated.failed()) {
					return Future.failedFuture(updated.cause());
				}
			}

			return super.attachLiveWriterIfAbsent(request);
		}
	}

	private static class AlwaysVersionConflictLoadRepository extends InMemoryIndexerLoadRepository {
		@Override
		public synchronized Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(
			AttachLiveWriterRequest request
		) {
			Future<Void> updated = updateState(new UpdateIndexerLoadState(
				request.indexerId(),
				IndexerLoadState.HISTORICAL_LOADING,
				request.expectedVersion()
			));
			if (updated.failed()) {
				return Future.failedFuture(updated.cause());
			}

			return super.attachLiveWriterIfAbsent(request);
		}
	}

	private static class WinnerAttachLoadRepository extends InMemoryIndexerLoadRepository {
		private Integer winnerId;

		@Override
		public Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(
			AttachLiveWriterRequest request
		) {
			return Future.succeededFuture(new AttachLiveWriterResult(
				false,
				winnerId,
				request.expectedVersion()
			));
		}
	}

	private static class BlockingAttachLoadRepository extends InMemoryIndexerLoadRepository {
		private int attachCalls;
		private AttachLiveWriterRequest pendingRequest;
		private Promise<AttachLiveWriterResult> pendingResult;

		@Override
		public synchronized Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(
			AttachLiveWriterRequest request
		) {
			attachCalls++;
			pendingRequest = request;
			pendingResult = Promise.promise();
			return pendingResult.future();
		}

		private synchronized void completeAttach() {
			super.attachLiveWriterIfAbsent(pendingRequest).onComplete(pendingResult);
		}
	}
}
