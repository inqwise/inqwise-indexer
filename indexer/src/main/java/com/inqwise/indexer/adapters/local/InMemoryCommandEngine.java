package com.inqwise.indexer.adapters.local;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandEngine;
import com.inqwise.indexer.commands.CommandExecutionOutcome;
import com.inqwise.indexer.commands.CommandFailureClassifier;
import com.inqwise.indexer.commands.CommandFailureKind;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandHandlerRegistry;
import com.inqwise.indexer.commands.CommandProcessor;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.routing.IndexerPublishingRouteException;

import io.vertx.core.Future;

public class InMemoryCommandEngine implements CommandEngine {
	private final CommandHandlerRegistry handlers;
	private final CommandProcessor processor;
	private final AtomicBoolean started = new AtomicBoolean();

	public InMemoryCommandEngine() {
		this(new CommandHandlerRegistry());
	}

	public InMemoryCommandEngine(CommandHandlerRegistry handlers) {
		this(handlers, failureClassifier());
	}

	static CommandFailureClassifier failureClassifier() {
		return new CommandFailureClassifier(List.of(
			CommandFailureClassifier.causeType(
				RetryableStaleStateException.class,
				CommandFailureKind.RETRYABLE
			),
			CommandFailureClassifier.causeType(
				IndexerPublishingRouteException.class,
				CommandFailureKind.RETRYABLE
			)
		));
	}

	public InMemoryCommandEngine(
		CommandHandlerRegistry handlers,
		CommandFailureClassifier failureClassifier
	) {
		this.handlers = Objects.requireNonNull(handlers, "handlers");
		this.processor = new CommandProcessor(handlers, failureClassifier);
	}

	@Override
	public InMemoryCommandEngine register(CommandHandler handler) {
		handlers.register(handler);
		return this;
	}

	@Override
	public Future<Void> submit(Command command) {
		Objects.requireNonNull(command, "command");

		return processor.execute(command)
			.compose(outcome -> outcome instanceof CommandExecutionOutcome.Failed failed
				? Future.failedFuture(failed.error())
				: Future.succeededFuture());
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
