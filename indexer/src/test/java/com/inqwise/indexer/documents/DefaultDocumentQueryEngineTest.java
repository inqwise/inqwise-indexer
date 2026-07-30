package com.inqwise.indexer.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.provisioning.definitions.IndexDefinition;
import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.publication.PublishedIndexResolver;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultDocumentQueryEngineTest {
	@Test
	void mergesPublishedIndexesAndAppliesGlobalPagination(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		PublishedIndexResolver resolver = ignored -> Future.succeededFuture(List.of(
			published(11, 1, "hn-a"),
			published(12, 2, "hn-b")
		));
		DefaultDocumentQueryEngine engine = new DefaultDocumentQueryEngine(resolver, store);
		DocumentQuery query = DocumentQuery.builder()
			.withTargetName("hacker-news")
			.withQueryText("local")
			.withFromInclusive(Instant.parse("2026-01-01T00:00:00Z"))
			.withToExclusive(Instant.parse("2027-01-01T00:00:00Z"))
			.withOffset(1)
			.withLimit(1)
			.build();
		IndexDefinition definition = IndexDefinition.builder()
			.withSchemaName("hn")
			.withSchemaVersion("v1")
			.build();

		store.ensure("hn-a", definition)
			.compose(ignored -> store.ensure("hn-b", definition))
			.compose(ignored -> store.put(
				"hn-a",
				"42",
				new JsonObject().put("text", "local local model")
			))
			.compose(ignored -> store.put(
				"hn-b",
				"43",
				new JsonObject().put("text", "local inference")
			))
			.compose(ignored -> engine.query(query))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(2, result.publishedIndexCount());
				assertEquals(1, result.offset());
				assertEquals(1, result.limit());
				assertFalse(result.hasMore());
				assertEquals(1, result.hits().size());
				assertEquals("43", result.hits().get(0).uid());
				assertEquals(12, result.hits().get(0).indexerId());
				assertEquals(2, result.hits().get(0).targetId());
				testContext.completeNow();
			})));
	}

	@Test
	void returnsEmptyWhenNoIndexIsPublished(VertxTestContext testContext) {
		PublishedIndexResolver resolver = ignored -> Future.succeededFuture(List.of());
		IndexerDocumentQueryProvider provider = ignored ->
			Future.failedFuture("provider must not be called");
		DocumentQueryEngine engine = new DefaultDocumentQueryEngine(resolver, provider);

		engine.query(DocumentQuery.builder()
				.withTargetName("hacker-news")
				.build())
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertTrue(result.hits().isEmpty());
				assertEquals(0, result.publishedIndexCount());
				assertFalse(result.hasMore());
				testContext.completeNow();
			})));
	}

	private static PublishedIndex published(int indexerId, int targetId, String indexName) {
		return PublishedIndex.builder()
			.withIndexerId(indexerId)
			.withTargetId(targetId)
			.withIndexName(indexName)
			.build();
	}
}
