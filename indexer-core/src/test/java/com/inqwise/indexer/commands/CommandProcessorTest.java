package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class CommandProcessorTest {
	@Test
	void returnsSucceededOutcome(VertxTestContext testContext) {
		CommandProcessor processor = processor(command -> Future.succeededFuture());

		processor.execute(command())
			.onComplete(testContext.succeeding(outcome -> testContext.verify(() -> {
				assertInstanceOf(CommandExecutionOutcome.Succeeded.class, outcome);
				testContext.completeNow();
			})));
	}

	@Test
	void classifiesAsynchronousFailureAndPreservesError(VertxTestContext testContext) {
		RuntimeException error = new RuntimeException("transient");
		CommandProcessor processor = processor(command -> Future.failedFuture(error));

		processor.execute(command())
			.onComplete(testContext.succeeding(outcome -> testContext.verify(() -> {
				CommandExecutionOutcome.Failed failed = assertInstanceOf(
					CommandExecutionOutcome.Failed.class,
					outcome
				);
				assertEquals(CommandFailureKind.RETRYABLE, failed.failureKind());
				assertSame(error, failed.error());
				testContext.completeNow();
			})));
	}

	@Test
	void classifiesSynchronousFailureAndPreservesError(VertxTestContext testContext) {
		IllegalArgumentException error = new IllegalArgumentException("invalid");
		CommandProcessor processor = processor(command -> {
			throw error;
		});

		processor.execute(command())
			.onComplete(testContext.succeeding(outcome -> testContext.verify(() -> {
				CommandExecutionOutcome.Failed failed = assertInstanceOf(
					CommandExecutionOutcome.Failed.class,
					outcome
				);
				assertEquals(CommandFailureKind.FINAL, failed.failureKind());
				assertSame(error, failed.error());
				testContext.completeNow();
			})));
	}

	@Test
	void missingHandlerIsFinalFailure(VertxTestContext testContext) {
		CommandProcessor processor = new CommandProcessor(
			new CommandHandlerRegistry(),
			new CommandFailureClassifier()
		);

		processor.execute(command())
			.onComplete(testContext.succeeding(outcome -> testContext.verify(() -> {
				CommandExecutionOutcome.Failed failed = assertInstanceOf(
					CommandExecutionOutcome.Failed.class,
					outcome
				);
				assertEquals(CommandFailureKind.FINAL, failed.failureKind());
				assertEquals(
					"No command handler for type: test.command",
					failed.error().getMessage()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void nullHandlerFutureIsFinalFailure(VertxTestContext testContext) {
		CommandProcessor processor = processor(command -> null);

		processor.execute(command())
			.onComplete(testContext.succeeding(outcome -> testContext.verify(() -> {
				CommandExecutionOutcome.Failed failed = assertInstanceOf(
					CommandExecutionOutcome.Failed.class,
					outcome
				);
				assertEquals(CommandFailureKind.FINAL, failed.failureKind());
				testContext.completeNow();
			})));
	}

	private CommandProcessor processor(CommandExecution execution) {
		CommandHandler handler = new CommandHandler() {
			@Override
			public String getType() {
				return "test.command";
			}

			@Override
			public Future<Void> handle(Command command) {
				return execution.execute(command);
			}
		};
		return new CommandProcessor(
			new CommandHandlerRegistry().register(handler),
			new CommandFailureClassifier()
		);
	}

	private Command command() {
		return new Command() {
			@Override
			public String getType() {
				return "test.command";
			}

			@Override
			public JsonObject toJson() {
				return new JsonObject();
			}
		};
	}

	@FunctionalInterface
	private interface CommandExecution {
		Future<Void> execute(Command command);
	}
}
