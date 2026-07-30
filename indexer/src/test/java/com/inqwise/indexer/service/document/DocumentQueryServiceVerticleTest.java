package com.inqwise.indexer.service.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.documents.DocumentHit;
import com.inqwise.indexer.documents.DocumentQuery;
import com.inqwise.indexer.documents.DocumentQueryEngine;
import com.inqwise.indexer.documents.DocumentQueryResult;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DocumentQueryServiceVerticleTest {
	@Test
	void queriesThroughConfiguredEventBusProxy(Vertx vertx, VertxTestContext testContext) {
		AtomicReference<DocumentQuery> captured = new AtomicReference<>();
		DocumentQueryEngine engine = query -> {
			captured.set(query);
			return Future.succeededFuture(DocumentQueryResult.builder()
				.withHits(List.of(DocumentHit.builder()
					.withIndexerId(11)
					.withTargetId(7)
					.withUid("42")
					.withScore(2.0d)
					.withDocument(new JsonObject().put("title", "Local LLM"))
					.build()))
				.withOffset(query.offset())
				.withLimit(query.limit())
				.withPublishedIndexCount(1)
				.build());
		};
		String address = DocumentQueryServices.DEFAULT_ADDRESS + ".test";
		DocumentQueryService proxy = DocumentQueryServices.proxy(vertx, address);

		vertx.deployVerticle(new DocumentQueryServiceVerticle(engine, address))
			.compose(ignored -> proxy.search(DocumentSearchRequest.builder()
				.withTargetName("hacker-news")
				.withQueryText("local")
				.withOffset(3)
				.withLimit(5)
				.build()))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals("hacker-news", captured.get().targetName());
				assertEquals("local", captured.get().queryText());
				assertEquals(3, captured.get().offset());
				assertEquals(5, result.getLimit());
				assertEquals(1, result.getPublishedIndexCount());
				assertEquals("42", result.getHits().get(0).getUid());
				assertEquals("Local LLM", result.getHits().get(0)
					.getDocument()
					.getString("title"));
				testContext.completeNow();
			})));
	}
}
