package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.commands.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.definitions.StaticTargetDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.hot.InMemoryInvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteSignatures;
import com.inqwise.indexer.metadata.ConcreteTargetKey;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetPeriod;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class SubmitIndexActionsCommandTest {
	@Test
	void metadataCommandExpandsTargetActionToWritableIndexers(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(firstIndexerId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_2",
				"queue-customers-2",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(secondIndexerId -> {
				PutDocumentActionItem action = PutDocumentActionItem.builder()
					.withTargetId(targetId)
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build();

				return eventBus.subscribe(events::add)
					.compose(ignored -> commandService.submit(new SubmitIndexActionsCommand(List.of(action))))
					.compose(ignored -> {
						assertEquals(0, events.size());
						assertConcretePut(
							queue.publishedByQueueName.get("queue-customers-1").get(0),
							targetId,
							firstIndexerId,
							"customers_1"
						);
						assertConcretePut(
							queue.publishedByQueueName.get("queue-customers-2").get(0),
							targetId,
							secondIndexerId,
							"customers_2"
						);
						return Future.succeededFuture();
					});
			})))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void metadataCommandSkipsNonRuntimeActiveWritableIndexersForTargetAction(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(ignored -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_2",
				"queue-customers-2",
				IndexerType.INDEX,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			))).compose(ignored -> {
				PutDocumentActionItem action = PutDocumentActionItem.builder()
					.withTargetId(targetId)
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build();

				return commandService.submit(new SubmitIndexActionsCommand(List.of(action)));
			}))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("No writable indexers found for target id: 1", error.getMessage());
				assertTrue(queue.published.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataCommandPublishesConcreteIndexerActionToOneQueue(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> {
				PutDocumentActionItem action = PutDocumentActionItem.builder()
					.withTargetId(targetId)
					.withIndexerId(indexerId)
					.withIndexName("customers_1")
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build();

				return commandService.submit(new SubmitIndexActionsCommand(List.of(action)))
					.compose(ignored -> {
						assertEquals(1, queue.published.size());
						assertConcretePut(
							queue.publishedByQueueName.get("queue-customers-1").get(0),
							targetId,
							indexerId,
							"customers_1"
						);
						return Future.succeededFuture();
					});
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void publicTargetCommandCreatesPeriodTargetAndWritableIndexer(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);
		List<IndexerMetadataChanged> events = new ArrayList<>();

		eventBus.subscribe(events::add)
			.compose(ignored -> {
			PutDocumentActionItem action = PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build();

			return commandService.submit(new SubmitIndexActionsCommand(
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(action)
			));
		}).compose(ignored -> repository.getTargetByDefinitionAndPeriod(
				new ConcreteTargetKey("customers", "2026-05")
			))
			.compose(found -> {
				assertTrue(found.isPresent());
				assertEquals("customers", found.get().targetName());
				return repository.listWritableIndexersByTargetId(found.get().id());
			})
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals(1, queue.published.size());
				assertEquals(1, events.size());
				assertEquals(indexers.get(0).id(), events.get(0).getIndexerId());
				assertEquals(SubmitIndexActionsCommand.TYPE, events.get(0).getCommandType());
				assertEquals(indexers.get(0).version(), events.get(0).getVersion());
				assertTrue(queue.publishedByQueueName.containsKey(indexers.get(0).queueName()));
				assertTrue(indexers.get(0).indexName().matches("customers--idx-[a-f0-9-]{36}"));
				assertTrue(indexers.get(0).queueName().matches("customers--queue-[a-f0-9-]{36}"));
				assertConcretePut(
					queue.published.get(0),
					indexers.get(0).targetId(),
					indexers.get(0).id(),
					indexers.get(0).indexName()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void publicTargetCommandFailsRetryableWhenTargetProvisioningInProgress(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.ensureTarget("customers", may2026Period())
			.compose(target -> repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
				target.id(),
				TargetProvisioningState.PROVISIONING,
				target.version()
			))).compose(ignored -> commandService.submit(new SubmitIndexActionsCommand(
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(PutDocumentActionItem.builder()
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build())
			))).onComplete(testContext.failing(error -> testContext.verify(() -> {
			CommandFailure failure = (CommandFailure) error;
			assertTrue(failure.retryable());
			assertEquals("Target provisioning is in progress: 1", failure.getMessage());
			assertTrue(queue.published.isEmpty());
			testContext.completeNow();
		})));
	}

	@Test
	void publicTargetCommandFailsFinalWhenTargetProvisioningFailed(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.ensureTarget("customers", may2026Period())
			.compose(target -> repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
				target.id(),
				TargetProvisioningState.FAILED,
				target.version()
			))).compose(ignored -> commandService.submit(new SubmitIndexActionsCommand(
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(PutDocumentActionItem.builder()
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build())
			))).onComplete(testContext.failing(error -> testContext.verify(() -> {
			CommandFailure failure = (CommandFailure) error;
			assertTrue(!failure.retryable());
			assertEquals("Target provisioning failed: 1", failure.getMessage());
			assertTrue(queue.published.isEmpty());
			testContext.completeNow();
		})));
	}

	@Test
	void publicTargetCommandFailsRetryableWhenProvisioningLockConflicts(
		VertxTestContext testContext
	) {
		ProvisioningLockConflictRepository repository =
			new ProvisioningLockConflictRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.ensureTarget("customers", may2026Period())
			.compose(target -> commandService.submit(new SubmitIndexActionsCommand(
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(PutDocumentActionItem.builder()
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build())
			)).recover(error -> repository.getTargetById(target.id()).compose(found -> {
			CommandFailure failure = (CommandFailure) error;
			assertTrue(failure.retryable());
			assertEquals("Target provisioning lock changed: 1", failure.getMessage());
			assertEquals(TargetProvisioningState.READY, found.get().provisioningState());
			assertTrue(queue.published.isEmpty());
			return Future.failedFuture(error);
		}))).onComplete(testContext.failing(error -> testContext.completeNow()));
	}

	@Test
	void publicTargetCommandRecordsStableInvalidRouteFailure(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryInvalidRouteCache invalidRouteCache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		InMemoryCommandService commandService = new InMemoryCommandService()
			.register(new SubmitIndexActionsCommandHandler(
				repository,
				emptyTargetDefinitionProvider(),
				eventBus,
				queue,
				invalidRouteCache
			));
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			List.of(PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build())
		);

		commandService.submit(command)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				CommandFailure failure = (CommandFailure) error;
				assertTrue(failure.stableInvalid());
				assertEquals("Target definition not found by name: customers", failure.getMessage());
				assertEquals(
					failure.getMessage(),
					invalidRouteCache.find(InvalidRouteSignatures.from(command).get(0))
						.orElseThrow()
						.reason()
				);
				assertTrue(queue.published.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void submitCommandRejectsOversizedActionBatch() {
		List<IndexerActionItem> actions = new ArrayList<>();
		for (int i = 0; i <= SubmitIndexActionsCommand.MAX_ACTIONS; i++) {
			actions.add(PutDocumentActionItem.builder()
				.withUid("doc-" + i)
				.withDocument(new JsonObject())
				.build());
		}

		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(actions)
		);
		assertEquals("Too many actions submitted: 1001", error.getMessage());
	}

	@Test
	void submitCommandRejectsOversizedDocument() {
		String oversized = "x".repeat(SubmitIndexActionsCommand.MAX_DOCUMENT_BYTES + 1);

		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(PutDocumentActionItem.builder()
					.withUid("42")
					.withDocument(new JsonObject().put("body", oversized))
					.build())
			)
		);
		assertTrue(error.getMessage().startsWith("Document is too large: "));
	}

	@Test
	void submitCommandRejectsEmptyActions() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(List.of())
		);
		assertEquals("No actions submitted", error.getMessage());
	}

	@Test
	void targetEnvelopeRejectsConcreteActionDestinations() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(PutDocumentActionItem.builder()
					.withTargetId(10)
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build())
			)
		);
		assertEquals("Target envelope actions must not include concrete destination fields", error.getMessage());
	}

	@Test
	void targetEnvelopeRejectsInternalActions() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(CompleteIndexActionItem.builder().build())
			)
		);
		assertEquals("Target envelope supports only document mutation actions: COMPLETE", error.getMessage());
	}

	@Test
	void concreteCommandRejectsTimestampWithoutTargetEnvelope() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(
				"command-1",
				null,
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(PutDocumentActionItem.builder()
					.withTargetId(10)
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build())
			)
		);
		assertEquals("Timestamp is allowed only with target envelope routing", error.getMessage());
	}

	@Test
	void concreteCommandRejectsIndexNameOnlyDestination() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(List.of(PutDocumentActionItem.builder()
				.withIndexName("customers_1")
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build()))
		);
		assertEquals("Concrete action requires target id or indexer id", error.getMessage());
	}

	@Test
	void concreteCommandRejectsInternalActionWithoutIndexerId() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(List.of(CompleteIndexActionItem.builder()
				.withTargetId(10)
				.build()))
		);
		assertEquals("Internal action requires concrete indexer id: COMPLETE", error.getMessage());
	}

	@Test
	void publicTargetCommandRequiresTimestampForPeriodTarget(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		commandService.submit(new SubmitIndexActionsCommand(
			"customers",
			null,
			List.of(PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build())
		)).onComplete(testContext.failing(error -> testContext.verify(() -> {
			assertEquals("Timestamp is required for target period strategy: MONTHLY", error.getMessage());
			assertTrue(queue.published.isEmpty());
			testContext.completeNow();
		})));
	}

	@Test
	void metadataCommandFailsConcreteIndexerActionWhenIndexerIsNotWritable(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.READ_ONLY
			)).compose(indexerId -> {
				PutDocumentActionItem action = PutDocumentActionItem.builder()
					.withTargetId(targetId)
					.withIndexerId(indexerId)
					.withIndexName("customers_1")
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build();

				return commandService.submit(new SubmitIndexActionsCommand(List.of(action)));
			}))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("Indexer is not writable: customers_1", error.getMessage());
				assertTrue(queue.published.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataCommandFailsConcreteIndexerActionWhenIndexerIsNotRuntimeActive(
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.NON_ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> {
				PutDocumentActionItem action = PutDocumentActionItem.builder()
					.withTargetId(targetId)
					.withIndexerId(indexerId)
					.withIndexName("customers_1")
					.withUid("42")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build();

				return commandService.submit(new SubmitIndexActionsCommand(List.of(action)));
			}))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("Indexer is not active: customers_1", error.getMessage());
				assertTrue(queue.published.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void metadataCommandPublishesCompleteActionToOneQueue(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> {
				CompleteIndexActionItem complete = CompleteIndexActionItem.builder()
					.withTargetId(targetId)
					.withIndexerId(indexerId)
					.build();

				return commandService.submit(new SubmitIndexActionsCommand(List.of(complete)))
					.compose(ignored -> {
						assertEquals(1, queue.published.size());
						assertEquals(complete.toJson(), queue.published.get(0).toJson());
						return Future.succeededFuture();
					});
			}))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void actionDestinationMissingFailsAtCommandConstruction() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new SubmitIndexActionsCommand(List.of(PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build()))
		);
		assertEquals("Concrete action destination is required", error.getMessage());
	}

	private InMemoryCommandService metadataCommandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus,
		RecordingQueue queue
	) {
		return new InMemoryCommandService()
			.register(new SubmitIndexActionsCommandHandler(
				repository,
				customersMonthlyTargetDefinitionProvider(),
				eventBus,
				queue
			));
	}

	private void assertConcretePut(
		IndexerActionItem item,
		Integer targetId,
		Integer indexerId,
		String indexName
	) {
		PutDocumentActionItem put = (PutDocumentActionItem) item;
		assertEquals(targetId, put.getTargetId());
		assertEquals(indexerId, put.getIndexerId());
		assertEquals(indexName, put.getIndexName());
		assertEquals("42", put.getUid());
		assertEquals("Ada", put.getDocument().getString("name"));
	}

	private TargetPeriod may2026Period() {
		return new TargetPeriod(
			TargetPeriodStrategy.MONTHLY,
			"2026-05",
			Instant.parse("2026-05-01T00:00:00Z"),
			Instant.parse("2026-06-01T00:00:00Z")
		);
	}

	private StaticTargetDefinitionProvider customersMonthlyTargetDefinitionProvider() {
		return new StaticTargetDefinitionProvider(List.of(
			new TargetDefinition("customers", TargetPeriodStrategy.MONTHLY)
		));
	}

	private StaticTargetDefinitionProvider emptyTargetDefinitionProvider() {
		return new StaticTargetDefinitionProvider(List.of());
	}

	private static class ProvisioningLockConflictRepository
		extends InMemoryDocumentStoreMetadataRepository {
		private final AtomicBoolean conflictOnce = new AtomicBoolean(true);

		@Override
		public Future<Void> updateTargetProvisioningState(UpdateTargetProvisioningState update) {
			if (update.provisioningState() == TargetProvisioningState.PROVISIONING
				&& conflictOnce.compareAndSet(true, false)) {
				return super.updateTargetProvisioningState(new UpdateTargetProvisioningState(
					update.id(),
					TargetProvisioningState.READY,
					update.expectedVersion()
				)).compose(ignored -> super.updateTargetProvisioningState(update));
			}

			return super.updateTargetProvisioningState(update);
		}
	}

	private static class RecordingQueue implements IndexerQueueClient {
		private final List<IndexerActionItem> published = new ArrayList<>();
		private final Map<String, List<IndexerActionItem>> publishedByQueueName =
			new LinkedHashMap<>();

		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.succeededFuture(new IndexerQueuePublisher() {
				@Override
				public Future<Void> publish(IndexerActionItem item) {
					published.add(item);
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
}
