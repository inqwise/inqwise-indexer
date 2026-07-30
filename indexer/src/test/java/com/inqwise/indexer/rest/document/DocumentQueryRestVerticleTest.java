package com.inqwise.indexer.rest.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.documents.DocumentHit;
import com.inqwise.indexer.documents.DocumentQuery;
import com.inqwise.indexer.documents.DocumentQueryEngine;
import com.inqwise.indexer.documents.DocumentQueryResult;
import com.inqwise.indexer.service.document.DocumentQueryServiceVerticle;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DocumentQueryRestVerticleTest {
	@Test
	void exposesBoundedPublishedDocumentSearch(Vertx vertx, VertxTestContext testContext) {
		AtomicReference<DocumentQuery> captured = new AtomicReference<>();
		DocumentQueryEngine engine = query -> {
			captured.set(query);
			return Future.succeededFuture(DocumentQueryResult.builder()
				.withHits(List.of(DocumentHit.builder()
					.withIndexerId(11)
					.withTargetId(7)
					.withUid("42")
					.withScore(3.0d)
					.withDocument(new JsonObject().put("title", "Local LLM"))
					.build()))
				.withOffset(query.offset())
				.withLimit(query.limit())
				.withPublishedIndexCount(1)
				.build());
		};
		DocumentQueryRestVerticle rest = new DocumentQueryRestVerticle(
			DocumentQueryRestOptions.builder().withPort(0).build()
		);

		vertx.deployVerticle(new DocumentQueryServiceVerticle(engine))
			.compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> request(
				vertx,
				rest.actualPort(),
				"/documents/search?target_name=hacker-news&q=local%20llm"
					+ "&from=2026-01-01T00%3A00%3A00Z"
					+ "&to=2027-01-01T00%3A00%3A00Z&offset=2&limit=5",
				200
			))
			.compose(result -> {
				assertEquals(1, result.getJsonArray("hits").size());
				assertEquals("42", result.getJsonArray("hits")
					.getJsonObject(0)
					.getString("uid"));
				assertEquals(1, result.getInteger("published_index_count"));
				return request(
					vertx,
					rest.actualPort(),
					"/documents/search?target_name=hacker-news&limit=101",
					400
				);
			})
			.onComplete(testContext.succeeding(error -> testContext.verify(() -> {
				assertEquals("local llm", captured.get().queryText());
				assertEquals(Instant.parse("2026-01-01T00:00:00Z"), captured.get().fromInclusive());
				assertEquals(Instant.parse("2027-01-01T00:00:00Z"), captured.get().toExclusive());
				assertEquals(2, captured.get().offset());
				assertEquals(5, captured.get().limit());
				testContext.completeNow();
			})));
	}

	private Future<JsonObject> request(
		Vertx vertx,
		int port,
		String uri,
		int expectedStatus
	) {
		return vertx.createHttpClient()
			.request(HttpMethod.GET, port, "127.0.0.1", uri)
			.compose(request -> request.send())
			.compose(response -> {
				assertEquals(expectedStatus, response.statusCode());
				return response.body().map(buffer ->
					buffer.length() == 0 ? new JsonObject() : buffer.toJsonObject());
			});
	}
}
