package com.inqwise.indexer.adapters.local;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.provisioning.definitions.QueueDefinition;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

public abstract class IndexerQueueResourceManagerContract {
	protected abstract IndexerQueueResourceManager createQueueResourceManager();

	@Test
	void deletingMissingQueueIsIdempotent(VertxTestContext testContext) {
		createQueueResourceManager().delete("missing-queue")
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void repeatedDeletionIsIdempotent(VertxTestContext testContext) {
		IndexerQueueResourceManager resources = createQueueResourceManager();

		resources.ensure("customers", definition(3))
			.compose(ignored -> resources.delete("customers"))
			.compose(ignored -> resources.delete("customers"))
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	@Test
	void deletionDoesNotModifyAnotherQueue(VertxTestContext testContext) {
		IndexerQueueResourceManager resources = createQueueResourceManager();

		resources.ensure("customers-a", definition(3))
			.compose(ignored -> resources.ensure("customers-b", definition(3)))
			.compose(ignored -> resources.delete("customers-a"))
			.compose(ignored -> resources.ensure("customers-b", definition(6)))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("different settings"));
				testContext.completeNow();
			})));
	}

	private QueueDefinition definition(int partitions) {
		return new QueueDefinition(new JsonObject().put("partitions", partitions));
	}
}
