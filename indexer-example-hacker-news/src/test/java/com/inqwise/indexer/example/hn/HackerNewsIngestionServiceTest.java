package com.inqwise.indexer.example.hn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.service.action.TargetActionService;
import com.inqwise.indexer.service.action.TargetActionSubmitRequest;
import com.inqwise.indexer.service.action.TargetActionSubmitResult;
import com.inqwise.indexer.service.action.TargetActionSubmitState;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class HackerNewsIngestionServiceTest {
	@Test
	void submitsOnlyChangedItemsAndUsesBoundedBatches(
		Vertx vertx,
		VertxTestContext testContext
	) {
		FakeClient client = new FakeClient();
		client.updates = List.of(1L, 1L, 2L, 3L);
		client.items.put(1L, item(1, 10));
		client.items.put(2L, item(2, 20));
		client.items.put(3L, HackerNewsItem.builder().withId(3).withDeleted(true).build());
		RecordingTargetActions targetActions = new RecordingTargetActions();
		HackerNewsIngestionService service = service(vertx, client, targetActions, 2);

		service.pollOnce()
			.compose(ignored -> service.pollOnce())
			.compose(ignored -> {
				client.items.put(2L, item(2, 21));
				return service.pollOnce();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(3, targetActions.requests.size());
				assertEquals(2, targetActions.requests.get(0).getActions().size());
				assertEquals(1, targetActions.requests.get(1).getActions().size());
				assertEquals(1, targetActions.requests.get(2).getActions().size());
				assertEquals(IndexerActionType.REMOVE_DOCUMENT,
					targetActions.requests.get(1).getActions().getFirst().getActionType());
				PutDocumentActionItem changed = (PutDocumentActionItem)
					targetActions.requests.get(2).getActions().getFirst();
				assertEquals(21, changed.getDocument().getInteger("score"));
				assertEquals(4, service.status().actionsSubmitted());
				assertEquals(5, service.status().unchangedItems());
				testContext.completeNow();
			})));
	}

	@Test
	void retriesAProjectionWhenSubmissionWasNotAccepted(
		Vertx vertx,
		VertxTestContext testContext
	) {
		FakeClient client = new FakeClient();
		client.updates = List.of(1L);
		client.items.put(1L, item(1, 10));
		RecordingTargetActions targetActions = new RecordingTargetActions();
		targetActions.failNext = true;
		HackerNewsIngestionService service = service(vertx, client, targetActions, 10);

		service.pollOnce()
			.recover(error -> Future.succeededFuture())
			.compose(ignored -> service.pollOnce())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(2, targetActions.requests.size());
				assertEquals(1, service.status().actionsSubmitted());
				testContext.completeNow();
			})));
	}

	private HackerNewsIngestionService service(
		Vertx vertx,
		HackerNewsClient client,
		TargetActionService targetActions,
		int batchSize
	) {
		return new HackerNewsIngestionService(
			vertx,
			client,
			targetActions,
			new HackerNewsDocumentProjector(),
			HackerNewsOptions.builder()
				.withPollInterval(Duration.ofHours(1))
				.withRequestConcurrency(2)
				.withActionBatchSize(batchSize)
				.build()
		);
	}

	private HackerNewsItem item(long id, int score) {
		return HackerNewsItem.builder()
			.withId(id)
			.withType("story")
			.withTime(1_700_000_000L + id)
			.withTitle("Story " + id)
			.withScore(score)
			.build();
	}

	private static final class FakeClient implements HackerNewsClient {
		private List<Long> updates = List.of();
		private final Map<Long, HackerNewsItem> items = new LinkedHashMap<>();

		@Override
		public Future<HackerNewsUpdates> fetchUpdates() {
			return Future.succeededFuture(HackerNewsUpdates.builder()
				.withItemIds(updates)
				.build());
		}

		@Override
		public Future<Optional<HackerNewsItem>> fetchItem(long id) {
			return Future.succeededFuture(Optional.ofNullable(items.get(id)));
		}
	}

	private static final class RecordingTargetActions implements TargetActionService {
		private final List<TargetActionSubmitRequest> requests = new ArrayList<>();
		private boolean failNext;

		@Override
		public Future<TargetActionSubmitResult> submit(TargetActionSubmitRequest request) {
			requests.add(request);
			if (failNext) {
				failNext = false;
				return Future.failedFuture("not accepted");
			}
			return Future.succeededFuture(TargetActionSubmitResult.builder()
				.withSubmissionId(request.getSubmissionId())
				.withState(TargetActionSubmitState.ACCEPTED)
				.build());
		}
	}
}
