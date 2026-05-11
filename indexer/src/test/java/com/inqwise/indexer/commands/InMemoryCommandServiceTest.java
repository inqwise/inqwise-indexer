package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryCommandServiceTest {
	@Test
	void dispatchesCommandToRegisteredHandler(VertxTestContext testContext) {
		TestCommand command = new TestCommand("test.echo", new JsonObject().put("value", "ok"));
		AtomicReference<Command> received = new AtomicReference<>();
		InMemoryCommandService service = new InMemoryCommandService()
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
		InMemoryCommandService service = new InMemoryCommandService();

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
