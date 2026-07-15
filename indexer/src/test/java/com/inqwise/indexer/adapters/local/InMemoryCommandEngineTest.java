package com.inqwise.indexer.adapters.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandHandlerRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryCommandEngineTest {
	@Test
	void dispatchesThroughInjectedHandlerRegistry(VertxTestContext testContext) {
		TestCommand command = new TestCommand("test.registry", new JsonObject());
		AtomicReference<Command> received = new AtomicReference<>();
		CommandHandlerRegistry handlers = new CommandHandlerRegistry()
			.register(new CommandHandler() {
				@Override
				public String getType() {
					return "test.registry";
				}

				@Override
				public Future<Void> handle(Command command) {
					received.set(command);
					return Future.succeededFuture();
				}
			});

		new InMemoryCommandEngine(handlers).submit(command)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertSame(command, received.get());
				testContext.completeNow();
			})));
	}

	@Test
	void lifecycleIsImmediatelyAvailable(VertxTestContext testContext) {
		InMemoryCommandEngine engine = new InMemoryCommandEngine();

		engine.start()
			.compose(ignored -> {
				assertTrue(engine.isStarted());
				return engine.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertFalse(engine.isStarted());
				testContext.completeNow();
			})));
	}

	@Test
	void dispatchesCommandToRegisteredHandler(VertxTestContext testContext) {
		TestCommand command = new TestCommand("test.echo", new JsonObject().put("value", "ok"));
		AtomicReference<Command> received = new AtomicReference<>();
		InMemoryCommandEngine service = new InMemoryCommandEngine()
			.register(new CommandHandler() {
				@Override
				public String getType() {
					return "test.echo";
				}

				@Override
				public Future<Void> handle(Command command) {
					received.set(command);
					return Future.succeededFuture();
				}
			});

		service.submit(command)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertSame(command, received.get());
				testContext.completeNow();
			})));
	}

	@Test
	void failsWhenHandlerIsMissing(VertxTestContext testContext) {
		TestCommand command = new TestCommand("test.missing", new JsonObject());
		InMemoryCommandEngine service = new InMemoryCommandEngine();

		service.submit(command)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("No command handler for type: test.missing", error.getMessage());
				testContext.completeNow();
			})));
	}

	private record TestCommand(String type, JsonObject json) implements Command {
		@Override
		public String getType() {
			return type;
		}

		@Override
		public JsonObject toJson() {
			return json.copy();
		}
	}
}
