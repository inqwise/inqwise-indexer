package com.inqwise.indexer.commands;

import java.util.Objects;

import io.vertx.core.Future;

public final class CommandProcessor {
	private final CommandHandlerRegistry handlers;
	private final CommandFailureClassifier failureClassifier;

	public CommandProcessor(
		CommandHandlerRegistry handlers,
		CommandFailureClassifier failureClassifier
	) {
		this.handlers = Objects.requireNonNull(handlers, "handlers");
		this.failureClassifier = Objects.requireNonNull(
			failureClassifier,
			"failureClassifier"
		);
	}

	public Future<CommandExecutionOutcome> execute(Command command) {
		Objects.requireNonNull(command, "command");
		return handlers.find(command.getType())
			.map(handler -> execute(handler, command))
			.orElseGet(() -> failed(CommandFailure.finalFailure(
				"No command handler for type: " + command.getType()
			)));
	}

	private Future<CommandExecutionOutcome> execute(
		CommandHandler handler,
		Command command
	) {
		try {
			Future<Void> handled = handler.handle(command);
			if (handled == null) {
				return failed(new IllegalStateException(
					"Command handler returned null future: " + handler.getType()
				));
			}

			return handled
				.map(ignored -> CommandExecutionOutcome.succeeded())
				.recover(this::failed);
		} catch (Throwable error) {
			return failed(error);
		}
	}

	private Future<CommandExecutionOutcome> failed(Throwable error) {
		return Future.succeededFuture(CommandExecutionOutcome.failed(
			failureClassifier.classify(error),
			error
		));
	}
}
