package com.inqwise.indexer.service.action;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.runtime.IndexerQueueClient;
import com.inqwise.indexer.runtime.IndexerQueueConsumer;
import com.inqwise.indexer.runtime.IndexerQueueConsumerOptions;
import com.inqwise.indexer.runtime.IndexerQueuePublisher;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.routing.RoutedIndexActionPublisher;
import com.inqwise.indexer.errors.IndexerErrorCodes;
import com.inqwise.indexer.hot.HotIndexActionsRequest;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.HotIndexer;
import com.inqwise.indexer.hot.HotMetadataView;
import com.inqwise.indexer.hot.HotTarget;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetActionServiceVerticleTest {
	@Test
	void requestJsonOmitsEmptyEnvelopeFields() {
		TargetActionSubmitRequest request = new TargetActionSubmitRequest()
			.setActions(List.of(IndexerActionItems.putDocument(
				"42",
				new JsonObject().put("name", "Ada")
			)));

		JsonObject json = request.toJson();
		JsonObject action = json.getJsonArray(TargetActionSubmitRequest.Keys.ACTIONS).getJsonObject(0);

		assertFalse(json.containsKey(TargetActionSubmitRequest.Keys.SUBMISSION_ID));
		assertFalse(json.containsKey(TargetActionSubmitRequest.Keys.TARGET_NAME));
		assertFalse(json.containsKey(TargetActionSubmitRequest.Keys.TIMESTAMP));
		assertFalse(action.containsKey("target_id"));
		assertFalse(action.containsKey("indexer_id"));
		assertFalse(action.containsKey("index_name"));
	}

	@Test
	void resultJsonOmitsEmptyFields() {
		JsonObject json = new TargetActionSubmitResult().toJson();

		assertFalse(json.containsKey(TargetActionSubmitResult.Keys.SUBMISSION_ID));
		assertFalse(json.containsKey(TargetActionSubmitResult.Keys.STATE));
	}

	@Test
	void submitsTargetActionsThroughServiceProxy(Vertx vertx, VertxTestContext testContext) {
		RecordingHotIndexActionsService hotActions = new RecordingHotIndexActionsService();

		vertx.deployVerticle(new TargetActionServiceVerticle(hotActions))
			.compose(ignored -> TargetActionServices.proxy(vertx).submit(new TargetActionSubmitRequest()
				.setSubmissionId("submit-1")
				.setTargetName("customers")
				.setTimestamp(Instant.parse("2026-06-06T10:15:00Z"))
				.setActions(List.of(IndexerActionItems.putDocument(
					"42",
					new JsonObject().put("name", "Ada")
				)))))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("submit-1", result.getSubmissionId());
				assertEquals(TargetActionSubmitState.ACCEPTED, result.getState());
				assertEquals("customers", hotActions.request.get().targetName());
				assertEquals(1, hotActions.request.get().actions().size());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsConcreteActionDestinations(VertxTestContext testContext) {
		RecordingHotIndexActionsService hotActions = new RecordingHotIndexActionsService();
		TargetActionService service = new TargetActionServiceImpl(hotActions);

		service.submit(new TargetActionSubmitRequest()
			.setTargetName("customers")
			.setActions(List.of(IndexerActionItems.concretePutDocument(
				10,
				20,
				"customers-2026-06",
				"42",
				new JsonObject().put("name", "Ada")
			))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertNull(hotActions.request.get());
				testContext.completeNow();
			})));
	}

	@Test
	void normalizesStableInvalidRouteFailure(VertxTestContext testContext) {
		RecordingHotIndexActionsService hotActions = new RecordingHotIndexActionsService(
			CommandFailure.stableInvalid("Target definition not found by name: customers")
		);
		TargetActionService service = new TargetActionServiceImpl(hotActions);

		service.submit(new TargetActionSubmitRequest()
			.setTargetName("customers")
			.setTimestamp(Instant.parse("2026-06-06T10:15:00Z"))
			.setActions(List.of(IndexerActionItems.putDocument(
				"42",
				new JsonObject().put("name", "Ada")
			))))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				ErrorTicket ticket = assertInstanceOf(ErrorTicket.class, error);
				assertEquals(IndexerErrorCodes.InvalidRoute, ticket.getError());
				assertEquals(404, ticket.getStatus());
				assertEquals("Target definition not found by name: customers", ticket.getErrorDetails());
				testContext.completeNow();
			})));
	}

	private static class RecordingHotIndexActionsService extends HotIndexActionsService {
		private final AtomicReference<HotIndexActionsRequest> request = new AtomicReference<>();
		private final RuntimeException failure;

		private RecordingHotIndexActionsService() {
			this(null);
		}

		private RecordingHotIndexActionsService(RuntimeException failure) {
			super(new EmptyHotMetadataView(), new RoutedIndexActionPublisher(new NoopQueue()), new NoopCommandService());
			this.failure = failure;
		}

		@Override
		public Future<Void> submit(HotIndexActionsRequest request) {
			this.request.set(request);
			if (failure != null) {
				return Future.failedFuture(failure);
			}

			return Future.succeededFuture();
		}
	}

	private static class EmptyHotMetadataView implements HotMetadataView {
		@Override
		public Optional<HotTarget> findTargetByName(String targetName) {
			return Optional.empty();
		}

		@Override
		public Optional<HotIndexer> findIndexerById(Integer indexerId) {
			return Optional.empty();
		}

		@Override
		public Future<Void> refreshHotTargetByConcreteTargetId(Integer targetId) {
			return Future.succeededFuture();
		}

		@Override
		public void invalidateHotTargetByConcreteTargetId(Integer targetId) {
		}

		@Override
		public void invalidateHotTargetByIndexerId(Integer indexerId) {
		}

		@Override
		public void invalidateAllHotTargets() {
		}
	}

	private static class NoopQueue implements IndexerQueueClient {
		@Override
		public Future<IndexerQueuePublisher> publisher(String queueName) {
			return Future.failedFuture("not used");
		}

		@Override
		public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
			return Future.failedFuture("not used");
		}
	}

	private static class NoopCommandService implements CommandService {
		@Override
		public Future<Void> submit(Command command) {
			return Future.failedFuture("not used");
		}
	}
}
