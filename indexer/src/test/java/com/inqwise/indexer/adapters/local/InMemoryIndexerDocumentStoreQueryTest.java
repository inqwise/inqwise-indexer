package com.inqwise.indexer.adapters.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.documents.DocumentIndexQuery;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryIndexerDocumentStoreQueryTest {
	@Test
	void queriesCopiesAndRemovesDocuments(Vertx vertx, VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		JsonObject first = new JsonObject()
			.put("title", "Run a local LLM")
			.put("text", "Local inference without an archive");
		JsonObject second = new JsonObject().put("title", "Remote API");

		store.ensure("hacker-news-1", IndexDefinition.builder()
				.withSchemaName("hn")
				.withSchemaVersion("v1")
				.build())
			.compose(ignored -> store.put("hacker-news-1", "42", first))
			.compose(ignored -> store.put("hacker-news-1", "43", second))
			.compose(ignored -> store.query(DocumentIndexQuery.builder()
				.withIndexName("hacker-news-1")
				.withQueryText("LOCAL llm")
				.withLimit(1)
				.build()))
			.compose(result -> {
				assertEquals(1, result.hits().size());
				assertEquals("42", result.hits().get(0).uid());
				assertFalse(result.hasMore());
				JsonObject returned = result.hits().get(0).document();
				returned.put("title", "mutated");
				assertEquals("Run a local LLM", store.get("hacker-news-1", "42").getString("title"));
				return store.query(DocumentIndexQuery.builder()
					.withIndexName("hacker-news-1")
					.withLimit(1)
					.build());
			})
			.compose(result -> {
				assertEquals(1, result.hits().size());
				assertTrue(result.hasMore());
				return store.remove("hacker-news-1", "42");
			})
			.compose(ignored -> store.query(DocumentIndexQuery.builder()
				.withIndexName("hacker-news-1")
				.withQueryText("local")
				.withLimit(10)
				.build()))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertTrue(result.hits().isEmpty());
				assertFalse(result.hasMore());
				testContext.completeNow();
			})));
	}
}
