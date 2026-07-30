package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.service.action.TargetActionServices;
import com.inqwise.indexer.service.action.TargetActionSubmitRequest;
import com.inqwise.indexer.service.document.DocumentQueryServices;
import com.inqwise.indexer.service.document.DocumentSearchRequest;
import com.inqwise.indexer.service.document.DocumentSearchResult;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DocumentQueryNodeIntegrationTest {
	@Test
	void indexesPublishesAndQueriesAColdTarget(
		Vertx vertx,
		VertxTestContext testContext
	) {
		IndexerNodeOptions options = IndexerNodeOptions.builder()
			.withTargetDefinitions(List.of(TargetDefinition.builder()
				.withTargetName("hacker-news")
				.withPeriodStrategy(TargetPeriodStrategy.NONE)
				.withAutoProvisionOnWrite(true)
				.withAutoPublishOnWrite(true)
				.build()))
			.build();
		IndexerNode node = IndexerNode.create(vertx, options);
		DocumentSearchRequest query = DocumentSearchRequest.builder()
			.withTargetName("hacker-news")
			.withQueryText("local llm")
			.build();
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

		node.start()
			.compose(ignored -> TargetActionServices.proxy(vertx).submit(
				TargetActionSubmitRequest.builder()
					.withSubmissionId("query-e2e-1")
					.withTargetName("hacker-news")
					.withActions(List.of(PutDocumentActionItem.builder()
						.withUid("42")
						.withDocument(new JsonObject()
							.put("title", "A local LLM workflow")
							.put("source", "hacker-news"))
						.build()))
					.build()
			))
			.compose(ignored -> awaitHit(vertx, query, deadline))
			.compose(result -> {
				assertEquals(1, result.getPublishedIndexCount());
				assertFalse(result.getHits().isEmpty());
				assertEquals("42", result.getHits().get(0).getUid());
				assertEquals(
					"A local LLM workflow",
					result.getHits().get(0).getDocument().getString("title")
				);
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private Future<DocumentSearchResult> awaitHit(
		Vertx vertx,
		DocumentSearchRequest query,
		long deadline
	) {
		return DocumentQueryServices.proxy(vertx).search(query).compose(result -> {
			if (!result.getHits().isEmpty()) {
				return Future.succeededFuture(result);
			}
			if (System.nanoTime() >= deadline) {
				return Future.failedFuture("Timed out waiting for indexed document");
			}
			Promise<DocumentSearchResult> retry = Promise.promise();
			vertx.setTimer(20L, ignored -> awaitHit(vertx, query, deadline)
				.onComplete(retry));
			return retry.future();
		});
	}
}
