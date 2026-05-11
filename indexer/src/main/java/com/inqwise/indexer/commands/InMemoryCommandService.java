package com.inqwise.indexer.commands;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;

public class InMemoryCommandService implements CommandService {
	private final Map<String, CommandHandler> handlersByType = new ConcurrentHashMap<>();

	public InMemoryCommandService register(CommandHandler handler) {
		Objects.requireNonNull(handler, "handler");
		handlersByType.put(handler.getType(), handler);
		return this;
	}

	@Override
	public Future<Void> submit(Command command) {
		Objects.requireNonNull(command, "command");

		CommandHandler handler = handlersByType.get(command.getType());
		if (handler == null) {
			return Future.failedFuture("No command handler for type: " + command.getType());
		}

		return handler.handle(command);
	}
}
