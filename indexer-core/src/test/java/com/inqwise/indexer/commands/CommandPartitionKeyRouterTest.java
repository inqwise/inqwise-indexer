package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class CommandPartitionKeyRouterTest {
	@Test
	void rejectsUnknownCommandType() {
		CommandPartitionKeyRouter router = new CommandPartitionKeyRouter();

		CommandFailure error = assertThrows(CommandFailure.class, () -> router.resolve(
			new GenericCommand("extension.unknown", new JsonObject())
		));

		assertEquals(CommandFailureKind.FINAL, error.kind());
		assertEquals(
			"No command partition-key resolver for type: extension.unknown",
			error.getMessage()
		);
	}

	@Test
	void rejectsDuplicateResolverRegistration() {
		CommandPartitionKeyRouter router = new CommandPartitionKeyRouter()
			.register("extension.command", command -> new CommandPartitionKey("extension:1"));

		assertThrows(IllegalArgumentException.class, () -> router.register(
			"extension.command",
			command -> new CommandPartitionKey("extension:2")
		));
	}
}
