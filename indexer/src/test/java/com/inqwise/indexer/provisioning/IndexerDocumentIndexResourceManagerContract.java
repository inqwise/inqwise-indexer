package com.inqwise.indexer.provisioning;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.provisioning.definitions.IndexDefinition;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

public abstract class IndexerDocumentIndexResourceManagerContract {
	protected abstract IndexerDocumentIndexResourceManager createDocumentIndexResourceManager();

	@Test
	void deletingMissingIndexIsIdempotent(VertxTestContext testContext) {
		createDocumentIndexResourceManager().delete("missing-index")
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void repeatedDeletionIsIdempotent(VertxTestContext testContext) {
		IndexerDocumentIndexResourceManager resources = createDocumentIndexResourceManager();

		resources.ensure("customers", definition("1"))
			.compose(ignored -> resources.delete("customers"))
			.compose(ignored -> resources.delete("customers"))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deletionDoesNotModifyAnotherIndex(VertxTestContext testContext) {
		IndexerDocumentIndexResourceManager resources = createDocumentIndexResourceManager();

		resources.ensure("customers-a", definition("1"))
			.compose(ignored -> resources.ensure("customers-b", definition("1")))
			.compose(ignored -> resources.delete("customers-a"))
			.compose(ignored -> resources.ensure("customers-b", definition("2")))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("different definition"));
				testContext.completeNow();
			})));
	}

	@Test
	void deletionRejectsNonConcreteIdentities(VertxTestContext testContext) {
		IndexerDocumentIndexResourceManager resources = createDocumentIndexResourceManager();

		Future.all(List.of("*", "customers-*", "customers-a,customers-b", "_all", " ")
			.stream()
			.map(indexName -> delete(resources, indexName).transform(result -> {
				assertTrue(result.failed());
				assertTrue(result.cause() instanceof IllegalArgumentException);
				return Future.succeededFuture();
			}))
			.toList())
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private Future<Void> delete(
		IndexerDocumentIndexResourceManager resources,
		String indexName
	) {
		try {
			return resources.delete(indexName);
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	private IndexDefinition definition(String version) {
		return new IndexDefinition(
			"customers",
			version,
			new JsonObject().put("shards", 1),
			new JsonObject()
		);
	}
}
