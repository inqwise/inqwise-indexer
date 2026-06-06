package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.QueueDefinition;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryResourceManagerTest {
	@Test
	void documentIndexEnsureIsIdempotentForSameDefinition(VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		IndexDefinition definition = indexDefinition("v1");

		store.ensure("customers", definition)
			.compose(ignored -> store.ensure("customers", definition))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void documentIndexEnsureFailsForDifferentDefinition(VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();

		store.ensure("customers", indexDefinition("v1"))
			.compose(ignored -> store.ensure("customers", indexDefinition("v2")))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals(
					"Document index resource already exists with different definition: customers",
					error.getMessage()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void documentIndexDeleteAllowsRecreateWithDifferentDefinition(VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();

		store.ensure("customers", indexDefinition("v1"))
			.compose(ignored -> store.delete("customers"))
			.compose(ignored -> store.ensure("customers", indexDefinition("v2")))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void queueEnsureIsIdempotentForSameSettings(VertxTestContext testContext) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();
		QueueDefinition definition = queueDefinition(3);

		queue.ensure("customers", definition)
			.compose(ignored -> queue.ensure("customers", definition))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void queueEnsureFailsForDifferentSettings(VertxTestContext testContext) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		queue.ensure("customers", queueDefinition(3))
			.compose(ignored -> queue.ensure("customers", queueDefinition(6)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals(
					"Queue resource already exists with different settings: customers",
					error.getMessage()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void queueDeleteAllowsRecreateWithDifferentSettings(VertxTestContext testContext) {
		InMemoryIndexerQueue queue = new InMemoryIndexerQueue();

		queue.ensure("customers", queueDefinition(3))
			.compose(ignored -> queue.delete("customers"))
			.compose(ignored -> queue.ensure("customers", queueDefinition(6)))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private IndexDefinition indexDefinition(String version) {
		return new IndexDefinition(
			"customers",
			version,
			new JsonObject().put("shards", 1),
			new JsonObject().put("properties", new JsonObject())
		);
	}

	private QueueDefinition queueDefinition(int partitions) {
		return new QueueDefinition(new JsonObject().put("partitions", partitions));
	}
}
