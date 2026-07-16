package com.inqwise.indexer.commands;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class CommandPartitionKeyRouter {
	private final Map<String, CommandPartitionKeyResolver> resolversByType = new HashMap<>();

	public CommandPartitionKeyRouter register(
		String commandType,
		CommandPartitionKeyResolver resolver
	) {
		Objects.requireNonNull(commandType, "commandType");
		Objects.requireNonNull(resolver, "resolver");
		if (resolversByType.putIfAbsent(commandType, resolver) != null) {
			throw new IllegalArgumentException(
				"Command partition-key resolver already registered: " + commandType
			);
		}
		return this;
	}

	public CommandPartitionKey resolve(Command command) {
		Objects.requireNonNull(command, "command");
		CommandPartitionKeyResolver resolver = resolversByType.get(command.getType());
		if (resolver == null) {
			throw CommandFailure.finalFailure(
				"No command partition-key resolver for type: " + command.getType()
			);
		}

		try {
			return Objects.requireNonNull(
				resolver.resolve(command),
				"Command partition-key resolver returned null: " + command.getType()
			);
		} catch (CommandFailure error) {
			throw error;
		} catch (RuntimeException error) {
			throw CommandFailure.finalFailure(
				"Invalid partition identity for command type: " + command.getType(),
				error
			);
		}
	}
}
