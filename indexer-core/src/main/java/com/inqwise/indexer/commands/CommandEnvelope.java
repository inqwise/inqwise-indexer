package com.inqwise.indexer.commands;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public record CommandEnvelope(
	String commandId,
	String commandType,
	int schemaVersion,
	Instant createdAt,
	String correlationId,
	JsonObject payload
) {
	public static final int CURRENT_SCHEMA_VERSION = 1;

	public CommandEnvelope {
		commandId = requireText(commandId, "commandId");
		commandType = requireText(commandType, "commandType");
		if (schemaVersion < 1) {
			throw new IllegalArgumentException("schemaVersion must be at least 1");
		}
		createdAt = Objects.requireNonNull(createdAt, "createdAt");
		if (correlationId != null) {
			correlationId = requireText(correlationId, "correlationId");
		}
		payload = Objects.requireNonNull(payload, "payload").copy();
	}

	public CommandEnvelope(JsonObject json) {
		this(
			json.getString("command_id"),
			json.getString("command_type"),
			json.getInteger("schema_version", CURRENT_SCHEMA_VERSION),
			Instant.parse(json.getString("created_at")),
			json.getString("correlation_id"),
			json.getJsonObject("payload")
		);
	}

	public static CommandEnvelope create(Command command) {
		Objects.requireNonNull(command, "command");
		return new CommandEnvelope(
			UUID.randomUUID().toString(),
			command.getType(),
			CURRENT_SCHEMA_VERSION,
			Instant.now(),
			command.getCorrelationId(),
			command.toJson()
		);
	}

	@Override
	public JsonObject payload() {
		return payload.copy();
	}

	public GenericCommand toCommand() {
		return new GenericCommand(commandType, correlationId, payload);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("command_id", commandId)
			.put("command_type", commandType)
			.put("schema_version", schemaVersion)
			.put("created_at", createdAt.toString())
			.put("correlation_id", correlationId)
			.put("payload", payload.copy());
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return value;
	}
}
