package com.inqwise.indexer.commands;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandHandlerRegistry {
	private final Map<String, CommandHandler> handlersByType = new ConcurrentHashMap<>();

	public CommandHandlerRegistry register(CommandHandler handler) {
		Objects.requireNonNull(handler, "handler");
		handlersByType.put(handler.getType(), handler);
		return this;
	}

	public Optional<CommandHandler> find(String commandType) {
		return Optional.ofNullable(handlersByType.get(Objects.requireNonNull(
			commandType,
			"commandType"
		)));
	}
}
