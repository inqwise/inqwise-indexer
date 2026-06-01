package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerActionItems;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueueConsumer;
import com.inqwise.indexer.IndexerQueueConsumerOptions;
import com.inqwise.indexer.IndexerQueuePublisher;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.RoutedIndexActionPublisher;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.metadata.ConcreteTargetKey;
import com.inqwise.indexer.metadata.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTargetDefinition;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetPeriod;
import com.inqwise.indexer.metadata.TargetPeriodResolver;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.providers.IndexerProviders;
import com.inqwise.indexer.providers.MetadataIndexerProvider;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HotIndexActionsServiceTest {
	@Test
	void submitsTargetEnvelopeThroughHotRoute(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueue queue = new RecordingQueue();
		RecordingCommandService commandService = new RecordingCommandService();
		DefaultHotMetadataView view = view(repository);
		HotIndexActionsService service = service(view, queue, commandService);

		insertReadyMonthlyTargetWithIndexer(repository)
			.compose(target -> view.refreshHotTargetByConcreteTargetId(target.id()))
			.compose(ignored -> service.submit(new HotIndexActionsRequest(
				null,
				"customers",
				Instant.parse("2026-05-18T10:15:00Z"),
				List.of(IndexerActionItems.putDocument(
					"42",
					new JsonObject().put("name", "Ada")
				))
			)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, queue.published.size());
				assertEquals(0, commandService.submitted.size());
				PutDocumentActionItem put = (PutDocumentActionItem) queue.published.get(0);
				assertEquals(1, put.getTargetId());
				assertEquals(1, put.getIndexerId());
				assertEquals("customers-index", put.getIndexName());
				testContext.completeNow();
			})));
	}

	@Test
	void submitsConcreteActionThroughDirectHotRoute(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueue queue = new RecordingQueue();
		RecordingCommandService commandService = new RecordingCommandService();
		DefaultHotMetadataView view = view(repository);
		HotIndexActionsService service = service(view, queue, commandService);

		insertReadyMonthlyTargetWithIndexer(repository)
			.compose(target -> view.refreshHotTargetByConcreteTargetId(target.id()))
			.compose(ignored -> service.submit(new HotIndexActionsRequest(
				null,
				null,
				null,
				List.of(IndexerActionItems.concretePutDocument(
					1,
					1,
					"customers-index",
					"42",
					new JsonObject().put("name", "Ada")
				))
			)))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, queue.published.size());
				assertEquals(0, commandService.submitted.size());
				PutDocumentActionItem put = (PutDocumentActionItem) queue.published.get(0);
				assertEquals(1, put.getIndexerId());
				assertEquals("customers-index", put.getIndexName());
				testContext.completeNow();
			})));
	}

	@Test
	void fallsBackUnchangedWhenHotTargetMissing(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueue queue = new RecordingQueue();
		RecordingCommandService commandService = new RecordingCommandService();
		HotIndexActionsService service = service(view(repository), queue, commandService);

		HotIndexActionsRequest request = new HotIndexActionsRequest(
			null,
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			List.of(IndexerActionItems.putDocument("42", new JsonObject()))
		);

		service.submit(request)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(0, queue.published.size());
				assertEquals(1, commandService.submitted.size());
				SubmitIndexActionsCommand command =
					(SubmitIndexActionsCommand) commandService.submitted.get(0);
				assertEquals("customers", command.getTargetName());
				assertEquals(request.timestamp(), command.getTimestamp());
				assertEquals(request.actions(), command.getActions());
				testContext.completeNow();
			})));
	}

	@Test
	void skipsFallbackWhenInvalidRouteCached(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueue queue = new RecordingQueue();
		RecordingCommandService commandService = new RecordingCommandService();
		InMemoryInvalidRouteCache invalidRouteCache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		HotIndexActionsService service = service(
			view(repository),
			queue,
			commandService,
			invalidRouteCache
		);
		HotIndexActionsRequest request = new HotIndexActionsRequest(
			null,
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			List.of(IndexerActionItems.putDocument("42", new JsonObject()))
		);
		InvalidRouteSignature signature = InvalidRouteSignatures.from(request).get(0);
		invalidRouteCache.record(signature, "Target definition not found by name: customers");

		service.submit(request)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				CommandFailure failure = (CommandFailure) error;
				assertTrue(failure.stableInvalid());
				assertEquals(
					"Invalid route cached: Target definition not found by name: customers",
					failure.getMessage()
				);
				assertEquals(0, queue.published.size());
				assertEquals(0, commandService.submitted.size());
				testContext.completeNow();
			})));
	}

	@Test
	void recordsStableInvalidFallbackFailure(VertxTestContext testContext) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		RecordingQueue queue = new RecordingQueue();
		RecordingCommandService commandService = new RecordingCommandService();
		commandService.failure = CommandFailure.stableInvalid(
			"Target definition not found by name: customers"
		);
		InMemoryInvalidRouteCache invalidRouteCache =
			new InMemoryInvalidRouteCache(Duration.ofMinutes(5));
		HotIndexActionsService service = service(
			view(repository),
			queue,
			commandService,
			invalidRouteCache
		);
		HotIndexActionsRequest request = new HotIndexActionsRequest(
			null,
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			List.of(IndexerActionItems.putDocument("42", new JsonObject()))
		);

		service.submit(request)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				InvalidRouteRecord record = invalidRouteCache.find(
					InvalidRouteSignatures.from(request).get(0)
				).orElseThrow();
				assertEquals("Target definition not found by name: customers", record.reason());
				assertEquals(1L, record.count());
				assertEquals(1, commandService.submitted.size());
				testContext.completeNow();
			})));
	}

	private HotIndexActionsService service(
		DefaultHotMetadataView view,
		RecordingQueue queue,
		RecordingCommandService commandService
	) {
		return service(view, queue, commandService, null);
	}

	private HotIndexActionsService service(
		DefaultHotMetadataView view,
		RecordingQueue queue,
		RecordingCommandService commandService,
		InvalidRouteCache invalidRouteCache
	) {
		return new HotIndexActionsService(
			view,
			new RoutedIndexActionPublisher(queue),
			commandService,
			invalidRouteCache
		);
	}

	private DefaultHotMetadataView view(InMemoryDocumentStoreMetadataRepository repository) {
		return new DefaultHotMetadataView(
			repository,
			new IndexerProviders(List.of(new MetadataIndexerProvider(repository)))
		);
	}

	private Future<TargetRecord> insertReadyMonthlyTargetWithIndexer(
		InMemoryDocumentStoreMetadataRepository repository
	) {
		TargetPeriodResolver resolver = new TargetPeriodResolver();
		return repository.insertTargetDefinition(new InsertTargetDefinition(
			"target-customers",
			"customers",
			TargetPeriodStrategy.MONTHLY,
			null
		)).compose(repository::getTargetDefinitionById)
			.compose(found -> {
				TargetPeriod period = resolver.resolve(
					TargetPeriodStrategy.MONTHLY,
					Instant.parse("2026-05-18T10:15:00Z")
				);
				return repository.ensureTarget(found.orElseThrow(), period);
			})
			.compose(target -> repository.insertIndexer(new InsertIndexer(
				"indexer-customers",
				target.id(),
				target.targetName(),
				"customers-index",
				"queue-customers",
				IndexerType.INDEX,
				IndexerRuntimeState.ACTIVE,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)).compose(indexerId -> repository.getTargetByDefinitionAndPeriod(
				new ConcreteTargetKey(target.targetDefinitionId(), target.periodKey())
			)).map(found -> found.orElseThrow()));
	}

	private static class RecordingQueue implements IndexerQueueClient {
		private final List<IndexerActionItem> published = new ArrayList<>();

		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.succeededFuture(new IndexerQueuePublisher() {
				@Override
				public Future<Void> publish(IndexerActionItem item) {
					published.add(item);
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

	private static class RecordingCommandService implements CommandService {
		private final List<Command> submitted = new ArrayList<>();
		private RuntimeException failure;

		@Override
		public Future<Void> submit(Command command) {
			submitted.add(command);
			if (failure != null) {
				return Future.failedFuture(failure);
			}

			return Future.succeededFuture();
		}
	}
}
