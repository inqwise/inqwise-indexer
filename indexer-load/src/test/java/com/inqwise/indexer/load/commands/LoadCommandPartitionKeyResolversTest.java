package com.inqwise.indexer.load.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.inqwise.indexer.commands.CommandPartitionKeyRouter;
import com.inqwise.indexer.commands.GenericCommand;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class LoadCommandPartitionKeyResolversTest {
	private final CommandPartitionKeyRouter router = createRouter();

	@Test
	void resolvesLoadLifecycleByLoadIndexer() {
		assertKey(PublishLoadCommand.TYPE, "indexer_id", 31, "indexer:31");
		assertKey(CleanupLoadCommand.TYPE, "indexer_id", 31, "indexer:31");
	}

	private static CommandPartitionKeyRouter createRouter() {
		CommandPartitionKeyRouter router = new CommandPartitionKeyRouter();
		LoadCommandPartitionKeyResolvers.registerWith(router);
		return router;
	}

	private void assertKey(
		String commandType,
		String field,
		Object identity,
		String expected
	) {
		GenericCommand command = new GenericCommand(
			commandType,
			new JsonObject().put(field, identity)
		);
		assertEquals(expected, router.resolve(command).value());
	}
}
