package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

class CommandEnvelopeTest {
	@Test
	void createsIndependentTransportIdentityAndExtractsCorrelation() {
		CommandEnvelope first = CommandEnvelope.create(command("correlation-1"));
		CommandEnvelope second = CommandEnvelope.create(command("correlation-1"));

		assertNotNull(first.commandId());
		assertNotEquals(first.commandId(), second.commandId());
		assertEquals("test.command", first.commandType());
		assertEquals(CommandEnvelope.CURRENT_SCHEMA_VERSION, first.schemaVersion());
		assertEquals("correlation-1", first.correlationId());
		assertEquals("value", first.payload().getString("field"));
	}

	@Test
	void codecRoundTripsEnvelope() {
		CommandEnvelope envelope = envelope();
		CommandEnvelopeCodec codec = new CommandEnvelopeCodec();

		Buffer encoded = codec.encode(envelope);
		CommandEnvelope decoded = codec.decode(encoded);

		assertEquals(envelope, decoded);
	}

	@Test
	void payloadAndGenericCommandAreDefensiveCopies() {
		CommandEnvelope envelope = envelope();
		JsonObject payload = envelope.payload();
		payload.put("field", "changed");

		GenericCommand command = envelope.toCommand();
		JsonObject commandPayload = command.toJson();
		commandPayload.put("field", "changed-again");

		assertEquals("value", envelope.payload().getString("field"));
		assertEquals("value", command.toJson().getString("field"));
		assertEquals(envelope.commandType(), command.getType());
		assertEquals(envelope.correlationId(), command.getCorrelationId());
	}

	@Test
	void envelopeExcludesPartitionAndDeliveryState() {
		JsonObject json = envelope().toJson();

		assertFalse(json.containsKey("partition_key"));
		assertFalse(json.containsKey("attempt"));
		assertFalse(json.containsKey("next_attempt_at"));
		assertFalse(json.containsKey("delivery_state"));
	}

	@Test
	void rejectsInvalidEnvelopeIdentityAndVersion() {
		assertThrows(IllegalArgumentException.class, () -> new CommandEnvelope(
			" ",
			"test.command",
			1,
			Instant.now(),
			null,
			new JsonObject()
		));
		assertThrows(IllegalArgumentException.class, () -> new CommandEnvelope(
			"command-1",
			"test.command",
			0,
			Instant.now(),
			null,
			new JsonObject()
		));
	}

	@Test
	void domainCommandPayloadsDoNotContainTransportIdentity() {
		assertFalse(new DeleteIndexerCommand(1, 0L).toJson().containsKey("command_id"));
		assertFalse(new CleanupDeletingIndexerCommand(1).toJson().containsKey("command_id"));
		assertFalse(new CreateIndexerCommand(
			"indexer-prefix",
			1,
			"customers",
			"customers-index",
			null,
			null,
			null,
			null,
			null,
			null,
			null
		).toJson().containsKey("command_id"));
	}

	private CommandEnvelope envelope() {
		return new CommandEnvelope(
			"command-1",
			"test.command",
			1,
			Instant.parse("2026-06-22T00:00:00Z"),
			"correlation-1",
			new JsonObject().put("field", "value")
		);
	}

	private Command command(String correlationId) {
		return new Command() {
			@Override
			public String getCorrelationId() {
				return correlationId;
			}

			@Override
			public String getType() {
				return "test.command";
			}

			@Override
			public JsonObject toJson() {
				return new JsonObject().put("field", "value");
			}
		};
	}
}
