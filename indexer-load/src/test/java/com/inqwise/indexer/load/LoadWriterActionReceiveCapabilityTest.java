package com.inqwise.indexer.load;

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
import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueueConsumer;
import com.inqwise.indexer.IndexerQueueConsumerOptions;
import com.inqwise.indexer.IndexerQueuePublisher;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.DeleteIndexerCommand;
import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.commands.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.definitions.StaticTargetDefinitionProvider;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.providers.ActionReceiveReadiness;
import com.inqwise.indexer.providers.IndexerPlugins;

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
		IndexerPlugins plugins = new IndexerPlugins(List.of(new LoadIndexerPlugin(metadata, loads)));
		RecordingEventBus eventBus = new RecordingEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commands = new InMemoryCommandService()
			.register(new SubmitIndexActionsCommandHandler(
				metadata,
				new StaticTargetDefinitionProvider(List.of()),
				eventBus,
				queue,
				null,
				plugins
			));

		insertLazyLoad(metadata, loads)
			.compose(load -> commands.submit(new SubmitIndexActionsCommand(List.of(
				PutDocumentActionItem.builder()
					.withTargetId(load.targetId())
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build()
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
			new LoadWriterActionReceiveCapability(metadata, loads);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.canReceive(
					loadWriter.orElseThrow(),
					PutDocumentActionItem.builder()
						.withTargetId(load.targetId())
						.withUid("42")
						.withDocument(new JsonObject().put("name", "Ada"))
						.build()
				).compose(readiness -> {
					assertEquals(ActionReceiveReadiness.REQUIRES_PREPARE, readiness);
					return capability.prepareToReceive(new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
						"command-1",
						null,
						loadWriter.orElseThrow(),
						List.of(PutDocumentActionItem.builder()
							.withTargetId(load.targetId())
							.withUid("42")
							.withDocument(new JsonObject().put("name", "Ada"))
							.build()),
						null
					)).compose(ignored -> capability.canReceive(
						loadWriter.orElseThrow(),
						PutDocumentActionItem.builder()
							.withTargetId(load.targetId())
							.withUid("43")
							.withDocument(new JsonObject().put("name", "Grace"))
							.build()
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
			new LoadWriterActionReceiveCapability(metadata, loads);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> {
					com.inqwise.indexer.providers.PrepareIndexerForActionsRequest firstRequest =
						new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
							"command-1",
							null,
							loadWriter.orElseThrow(),
							List.of(),
							null
						);
					com.inqwise.indexer.providers.PrepareIndexerForActionsRequest secondRequest =
						new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
							"command-2",
							null,
							loadWriter.orElseThrow(),
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
				assertEquals(first.indexers().get(0).id(), second.indexers().get(0).id());
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
			new LoadWriterActionReceiveCapability(metadata, loads, commands, events);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.prepareToReceive(new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
					"command-1",
					null,
					loadWriter.orElseThrow(),
					List.of(PutDocumentActionItem.builder()
						.withTargetId(load.targetId())
						.withUid("42")
						.withDocument(new JsonObject().put("name", "Ada"))
						.build()),
					null
				)).compose(prepared -> loads.getByIndexerId(load.indexerId())
					.map(updated -> new PrepareResult(prepared, updated.orElseThrow())))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(1, result.prepared().indexers().size());
				assertEquals(IndexerRole.LIVE_WRITER, result.prepared().indexers().get(0).role());
				assertEquals(result.prepared().indexers().get(0).id(), result.load().liveIndexerId());
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
		LoadWriterActionReceiveCapability capability = new LoadWriterActionReceiveCapability(
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
							null,
							loadWriter.orElseThrow(),
							List.of(),
							null
						)
					).map(prepared -> new WinnerResult(prepared, winnerId));
				})))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(result.winnerId(), result.prepared().indexers().get(0).id());
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
		LoadWriterActionReceiveCapability capability = new LoadWriterActionReceiveCapability(
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
						null,
						loadWriter.orElseThrow(),
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
			new LoadWriterActionReceiveCapability(metadata, loads);

		insertLazyLoad(metadata, loads)
			.compose(load -> metadata.getIndexerById(load.indexerId())
				.compose(loadWriter -> capability.prepareToReceive(new com.inqwise.indexer.providers.PrepareIndexerForActionsRequest(
					"command-1",
					null,
					loadWriter.orElseThrow(),
					List.of(PutDocumentActionItem.builder()
						.withTargetId(load.targetId())
						.withUid("42")
						.withDocument(new JsonObject().put("name", "Ada"))
						.build()),
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

	private void assertConcretePut(IndexerActionItem item, IndexerRecord liveWriter) {
		PutDocumentActionItem put = (PutDocumentActionItem) item;
		assertEquals(liveWriter.targetId(), put.getTargetId());
		assertEquals(liveWriter.id(), put.getIndexerId());
		assertEquals(liveWriter.indexName(), put.getIndexName());
		assertEquals("42", put.getUid());
		assertEquals("Ada", put.getDocument().getString("name"));
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
		public Future<Void> subscribe(Handler<IndexerMetadataChanged> handler) {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> subscribeTarget(Handler<TargetMetadataChanged> handler) {
			return Future.succeededFuture();
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
