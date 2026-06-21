package com.inqwise.indexer.commands;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import io.vertx.core.Future;

public class InMemoryCommandEngine implements CommandEngine {
	private final CommandHandlerRegistry handlers;
	private final AtomicBoolean started = new AtomicBoolean();

	public InMemoryCommandEngine() {
		this(new CommandHandlerRegistry());
	}

	public InMemoryCommandEngine(CommandHandlerRegistry handlers) {
		this.handlers = Objects.requireNonNull(handlers, "handlers");
	}

	@Override
	public InMemoryCommandEngine register(CommandHandler handler) {
		handlers.register(handler);
		return this;
	}

	@Override
	public Future<Void> submit(Command command) {
		Objects.requireNonNull(command, "command");

		return handlers.find(command.getType())
			.map(handler -> handler.handle(command))
			.orElseGet(() -> Future.failedFuture(
				"No command handler for type: " + command.getType()
			));
	}

	@Override
	public Future<Void> start() {
		started.set(true);
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> stop() {
		started.set(false);
		return Future.succeededFuture();
	}

	public boolean isStarted() {
		return started.get();
	}
}
