package com.inqwise.indexer.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.inqwise.indexer.commands.CommandPartitionKeyRouter;
import com.inqwise.indexer.commands.GenericCommand;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class CleanupCommandPartitionKeyResolversTest {
	private final CommandPartitionKeyRouter router = createRouter();

	@Test
	void resolvesCleanupWorkflowByIndexer() {
		assertKey(DeleteIndexerCommand.TYPE, 31, "indexer:31");
		assertKey(CleanupResetIndexerQueueCommand.TYPE, 31, "indexer:31");
		assertKey(CleanupDeletingIndexerCommand.TYPE, 31, "indexer:31");
	}

	private static CommandPartitionKeyRouter createRouter() {
		CommandPartitionKeyRouter router = CommandPartitionKeyRouter.withCoreResolvers();
		CleanupCommandPartitionKeyResolvers.registerWith(router);
		return router;
	}

	private void assertKey(String commandType, Integer indexerId, String expected) {
		GenericCommand command = new GenericCommand(
			commandType,
			new JsonObject().put("indexer_id", indexerId)
		);
		assertEquals(expected, router.resolve(command).value());
	}
}
