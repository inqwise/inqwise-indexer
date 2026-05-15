package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.inqwise.indexer.commands.InMemoryCommandService;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.commands.SubmitIndexActionsCommandHandler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class SubmitIndexActionsCommandTest {
	@Test
	void coldCommandCreatesIndexerAndPublishesActions(VertxTestContext testContext) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = commandService(repository, eventBus, queue);
		List<IndexerLifecycleChanged> events = new ArrayList<>();
		PutDocumentActionItem action = PutDocumentActionItem.builder()
			.withIndexName("customers_1")
			.withUid("42")
			.withDocument(new JsonObject().put("name", "Ada"))
			.build();
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(
			"command-1",
			"batch-1",
			10,
			"customers",
			"customers_1",
			List.of(action)
		);

		eventBus.subscribe(events::add)
			.compose(ignored -> commandService.submit(command))
			.compose(ignored -> repository.list())
			.onComplete(testContext.succeeding(models -> testContext.verify(() -> {
				assertEquals(1, models.size());
				assertEquals(IndexerStatus.STARTED, models.get(0).getStatus());
				assertEquals("customers_1", models.get(0).getIndexName());
				assertEquals(1, events.size());
				assertEquals(SubmitIndexActionsCommand.TYPE, events.get(0).getCommandType());
				assertEquals(1, queue.published.size());
				assertEquals(action.toJson(), queue.published.get(0).toJson());
				testContext.completeNow();
			})));
	}

	@Test
	void completeActionIsPublishedAsSubmittedAction(VertxTestContext testContext) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = commandService(repository, eventBus, queue);
		CompleteIndexActionItem complete = new CompleteIndexActionItem();
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(
			"customers",
			"customers_1",
			List.of(complete)
		);

		commandService.submit(command)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, queue.published.size());
				assertEquals(complete.toJson(), queue.published.get(0).toJson());
				testContext.completeNow();
			})));
	}

	@Test
	void deletedIndexerFailsClosedAndDoesNotPublish(VertxTestContext testContext) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = commandService(repository, eventBus, queue);
		IndexerModel deleted = IndexerModel.builder()
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.withStatus(IndexerStatus.DELETED)
			.build();
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(
			"customers",
			"customers_1",
			List.of(PutDocumentActionItem.builder()
				.withIndexName("customers_1")
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build())
		);

		repository.save(deleted)
			.compose(ignored -> commandService.submit(command))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("Indexer is deleted: customers_1", error.getMessage());
				assertTrue(queue.published.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void actionIndexMismatchFailsBeforePublish(VertxTestContext testContext) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		RecordingQueue queue = new RecordingQueue();
		InMemoryCommandService commandService = commandService(repository, eventBus, queue);
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(
			"customers",
			"customers_1",
			List.of(PutDocumentActionItem.builder()
				.withIndexName("customers_2")
				.withUid("42")
				.withDocument(new JsonObject().put("name", "Ada"))
				.build())
		);

		commandService.submit(command)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().startsWith("Action index mismatch"));
				assertTrue(queue.published.isEmpty());
				testContext.completeNow();
			})));
	}

	private InMemoryCommandService commandService(
		InMemoryIndexerRepository repository,
		InMemoryIndexerLifecycleEventBus eventBus,
		RecordingQueue queue
	) {
		return new InMemoryCommandService()
			.register(new SubmitIndexActionsCommandHandler(repository, eventBus, queue));
	}

	private static class RecordingQueue implements IndexerQueue {
		private final List<IndexerActionItem> published = new ArrayList<>();

		@Override
		public Future<Void> publish(IndexerActionItem item) {
			published.add(item);
			return Future.succeededFuture();
		}

		@Override
		public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
			return Future.failedFuture("consumer is not expected");
		}

		@Override
		public Future<Void> close() {
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> delete() {
			return Future.succeededFuture();
		}
	}
}
