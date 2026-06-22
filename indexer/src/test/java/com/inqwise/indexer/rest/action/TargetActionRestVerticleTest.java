package com.inqwise.indexer.rest.action;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueueConsumer;
import com.inqwise.indexer.IndexerQueueConsumerOptions;
import com.inqwise.indexer.IndexerQueuePublisher;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.RoutedIndexActionPublisher;
import com.inqwise.indexer.hot.HotIndexActionsRequest;
import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.hot.HotIndexer;
import com.inqwise.indexer.hot.HotMetadataView;
import com.inqwise.indexer.hot.HotTarget;
import com.inqwise.indexer.service.action.TargetActionServiceVerticle;
import com.inqwise.indexer.service.action.TargetActionSubmitState;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetActionRestVerticleTest {
	@Test
	void submitsTargetActionsOverHttp(Vertx vertx, VertxTestContext testContext) {
		RecordingHotIndexActionsService hotActions = new RecordingHotIndexActionsService();
		TargetActionRestVerticle restVerticle = new TargetActionRestVerticle(
			new TargetActionRestOptions().setPort(0)
		);

		vertx.deployVerticle(new TargetActionServiceVerticle(hotActions))
			.compose(ignored -> vertx.deployVerticle(restVerticle))
			.compose(ignored -> request(
				vertx,
				restVerticle.actualPort(),
				"/targets/customers/actions",
				new JsonObject()
					.put("submission_id", "submit-1")
					.put("timestamp", "2026-06-06T10:15:00Z")
					.put("actions", io.vertx.core.json.JsonArray.of(
						new JsonObject()
							.put("type", "PUT_DOCUMENT")
							.put("uid", "42")
							.put("document", new JsonObject().put("name", "Ada"))
					))
			))
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				JsonObject result = body.toJsonObject();
				assertEquals("submit-1", result.getString("submission_id"));
				assertEquals(TargetActionSubmitState.ACCEPTED.name(), result.getString("state"));
				assertEquals("customers", hotActions.request.get().targetName());
				assertEquals(Instant.parse("2026-06-06T10:15:00Z"), hotActions.request.get().timestamp());
				assertEquals(1, hotActions.request.get().actions().size());
				testContext.completeNow();
			})));
	}

	private Future<Buffer> request(Vertx vertx, int port, String uri, JsonObject body) {
		return vertx.createHttpClient()
			.request(HttpMethod.POST, port, "127.0.0.1", uri)
			.compose(request -> request
				.putHeader("content-type", "application/json")
				.send(body.encode())
				.compose(response -> {
					assertEquals(200, response.statusCode());
					return response.body();
				}));
	}

	private static class RecordingHotIndexActionsService extends HotIndexActionsService {
		private final AtomicReference<HotIndexActionsRequest> request = new AtomicReference<>();

		private RecordingHotIndexActionsService() {
			super(new EmptyHotMetadataView(), new RoutedIndexActionPublisher(new NoopQueue()), new NoopCommandService());
		}

		@Override
		public Future<Void> submit(HotIndexActionsRequest request) {
			this.request.set(request);
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
