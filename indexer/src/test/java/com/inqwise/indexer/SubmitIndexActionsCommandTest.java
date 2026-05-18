package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.commands.SubmitIndexActionsCommandHandler;
import com.inqwise.indexer.metadata.ConcreteTargetKey;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.InsertTargetDefinition;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;

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
		List<IndexerLifecycleChanged> events = new ArrayList<>();

		repository.insertTarget(new InsertTarget(null, "customers", null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_1",
				"queue-customers-1",
				IndexerType.INDEX,
				IndexerRuntimeStatus.STARTED,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(firstIndexerId -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_2",
				"queue-customers-2",
				IndexerType.INDEX,
				IndexerRuntimeStatus.STARTED,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(secondIndexerId -> {
				PutDocumentActionItem action = PutDocumentActionItem.builder()
					.withTargetId(targetId)
					.withUid("42")
					.withSequence(100L)
					.withMutationId("mutation-1")
					.withDocument(new JsonObject().put("name", "Ada"))
					.build();

				return eventBus.subscribe(events::add)
					.compose(ignored -> commandService.submit(new SubmitIndexActionsCommand(List.of(action))))
					.compose(ignored -> {
						assertEquals(2, events.size());
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
				IndexerRuntimeStatus.NON_ACTIVE,
				PublicationState.PUBLISHED,
				MutationState.WRITABLE
			)).compose(ignored -> repository.insertIndexer(new InsertIndexer(
				null,
				targetId,
				"customers",
				"customers_2",
				"queue-customers-2",
				IndexerType.INDEX,
				IndexerRuntimeStatus.DELETED,
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
				IndexerRuntimeStatus.STARTED,
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

		repository.insertTargetDefinition(new InsertTargetDefinition(
			"target-customers",
			"customers",
			TargetPeriodStrategy.MONTHLY,
			null
		)).compose(ignored -> {
			PutDocumentActionItem action = PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build();

			return commandService.submit(new SubmitIndexActionsCommand(
				"target-customers",
				null,
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(action)
			));
		}).compose(ignored -> repository.getTargetDefinitionByUid("target-customers"))
			.compose(found -> repository.getTargetByDefinitionAndPeriod(
				new ConcreteTargetKey(found.get().id(), "2026-05")
			))
			.compose(found -> {
				assertTrue(found.isPresent());
				assertEquals("customers--2026-05", found.get().targetName());
				return repository.listWritableIndexersByTargetId(found.get().id());
			})
			.onComplete(testContext.succeeding(indexers -> testContext.verify(() -> {
				assertEquals(1, indexers.size());
				assertEquals(1, queue.published.size());
				assertTrue(queue.publishedByQueueName.containsKey(indexers.get(0).queueName()));
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
	void publicTargetCommandRequiresTimestampForPeriodTarget(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);

		repository.insertTargetDefinition(new InsertTargetDefinition(
			"target-customers",
			"customers",
			TargetPeriodStrategy.MONTHLY,
			null
		)).compose(ignored -> commandService.submit(new SubmitIndexActionsCommand(
			"target-customers",
			null,
			null,
			List.of(PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build())
		))).onComplete(testContext.failing(error -> testContext.verify(() -> {
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
				IndexerRuntimeStatus.STARTED,
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
				IndexerRuntimeStatus.NON_ACTIVE,
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
				IndexerRuntimeStatus.STARTED,
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
	void actionDestinationMissingFailsBeforePublish(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = metadataCommandService(repository, eventBus, queue);
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(
			List.of(PutDocumentActionItem.builder()
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build())
		);

		commandService.submit(command)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Action destination is missing"));
				assertTrue(queue.published.isEmpty());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService metadataCommandService(
		InMemoryDocumentStoreMetadataRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus,
		RecordingQueue queue
	) {
		return new InMemoryCommandService()
			.register(new SubmitIndexActionsCommandHandler(repository, eventBus, queue));
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
