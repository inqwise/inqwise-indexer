package com.inqwise.indexer.example.hn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.service.action.TargetActionService;
import com.inqwise.indexer.service.action.TargetActionServices;
import com.inqwise.indexer.service.action.TargetActionServiceVertxProxyHandler;
import com.inqwise.indexer.service.action.TargetActionSubmitRequest;
import com.inqwise.indexer.service.action.TargetActionSubmitResult;
import com.inqwise.indexer.service.action.TargetActionSubmitState;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HackerNewsClusterSubmissionTest {
	@Test
	void submitsProjectionThroughTargetActionEventBusProxy(
		Vertx vertx,
		VertxTestContext testContext
	) {
		RecordingTargetActions targetActions = new RecordingTargetActions();
		TargetActionServiceVertxProxyHandler handler =
			new TargetActionServiceVertxProxyHandler(vertx, targetActions);
		MessageConsumer<JsonObject> registration = handler.register(
			vertx.eventBus(),
			TargetActionServices.DEFAULT_ADDRESS
		);
		HackerNewsIngestionService ingestion = new HackerNewsIngestionService(
			vertx,
			new OneItemClient(),
			TargetActionServices.proxy(vertx),
			new HackerNewsDocumentProjector(),
			HackerNewsOptions.builder().withPollInterval(Duration.ofHours(1)).build()
		);

		ingestion.pollOnce()
			.map(ignored -> testContext.verify(() -> {
				assertEquals(1, targetActions.requests.size());
				TargetActionSubmitRequest request = targetActions.requests.getFirst();
				assertEquals(HackerNewsOptions.TARGET_NAME, request.getTargetName());
				PutDocumentActionItem action =
					(PutDocumentActionItem) request.getActions().getFirst();
				assertEquals("42", action.getUid());
				assertEquals("Sent across EventBus", action.getDocument().getString("title"));
			}))
			.eventually(registration::unregister)
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private static final class OneItemClient implements HackerNewsClient {
		@Override
		public Future<HackerNewsUpdates> fetchUpdates() {
			return Future.succeededFuture(HackerNewsUpdates.builder()
				.withItemIds(List.of(42L))
				.build());
		}

		@Override
		public Future<Optional<HackerNewsItem>> fetchItem(long id) {
			return Future.succeededFuture(Optional.of(HackerNewsItem.builder()
				.withId(id)
				.withType("story")
				.withTitle("Sent across EventBus")
				.build()));
		}
	}

	private static final class RecordingTargetActions implements TargetActionService {
		private final List<TargetActionSubmitRequest> requests = new ArrayList<>();

		@Override
		public Future<TargetActionSubmitResult> submit(TargetActionSubmitRequest request) {
			requests.add(request);
			return Future.succeededFuture(TargetActionSubmitResult.builder()
				.withSubmissionId(request.getSubmissionId())
				.withState(TargetActionSubmitState.ACCEPTED)
				.build());
		}
	}
}
