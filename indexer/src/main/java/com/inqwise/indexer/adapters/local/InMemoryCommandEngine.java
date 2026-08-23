package com.inqwise.indexer.adapters.local;

import java.time.Duration;
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
import com.inqwise.indexer.commands.CommandRetryDecision;
import com.inqwise.indexer.commands.CommandRetryDecisionType;
import com.inqwise.indexer.commands.CommandRetryPolicy;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.routing.IndexerPublishingRouteException;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class InMemoryCommandEngine implements CommandEngine {
	private final CommandHandlerRegistry handlers;
	private final CommandProcessor processor;
	private final CommandRetryPolicy retryPolicy;
	private final AtomicBoolean started = new AtomicBoolean();

	public InMemoryCommandEngine() {
		this(new CommandHandlerRegistry());
	}

	public InMemoryCommandEngine(CommandHandlerRegistry handlers) {
		this(handlers, failureClassifier(), defaultRetryPolicy());
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

	static CommandRetryPolicy defaultRetryPolicy() {
		return CommandRetryPolicy.builder()
			.withMaxAttempts(4)
			.withInitialDelay(Duration.ofMillis(100))
			.withMaximumDelay(Duration.ofMillis(500))
			.withMultiplier(2)
			.withJitterRatio(0)
			.build();
	}

	public InMemoryCommandEngine(
		CommandHandlerRegistry handlers,
		CommandFailureClassifier failureClassifier
	) {
		this(handlers, failureClassifier, defaultRetryPolicy());
	}

	public InMemoryCommandEngine(
		CommandHandlerRegistry handlers,
		CommandFailureClassifier failureClassifier,
		CommandRetryPolicy retryPolicy
	) {
		this.handlers = Objects.requireNonNull(handlers, "handlers");
		this.processor = new CommandProcessor(handlers, failureClassifier);
		this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
	}

	@Override
	public InMemoryCommandEngine register(CommandHandler handler) {
		handlers.register(handler);
		return this;
	}

	@Override
	public Future<Void> submit(Command command) {
		Objects.requireNonNull(command, "command");
		return submit(command, 1);
	}

	private Future<Void> submit(Command command, int attempt) {
		return processor.execute(command)
			.compose(outcome -> {
				if (outcome instanceof CommandExecutionOutcome.Failed failed) {
					CommandRetryDecision decision = retryPolicy.decide(
						failed.failureKind(),
						attempt,
						0
					);
					if (decision.type() == CommandRetryDecisionType.RETRY) {
						Duration delay = decision.delay().orElseThrow();
						if (!delay.isZero() && Vertx.currentContext() == null) {
							return Future.failedFuture(failed.error());
						}
						return delay(delay)
							.compose(ignored -> submit(command, attempt + 1));
					}
					return Future.failedFuture(failed.error());
				}

				return Future.succeededFuture();
			});
	}

	private Future<Void> delay(Duration delay) {
		if (delay.isZero()) {
			return Future.succeededFuture();
		}

		Context context = Vertx.currentContext();
		if (context == null) {
			return Future.succeededFuture();
		}

		Promise<Void> promise = Promise.promise();
		context.owner().setTimer(delay.toMillis(), ignored -> promise.complete());
		return promise.future();
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
