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

	public static Builder builder() {
		return new Builder();
	}

	public static CommandEnvelope create(Command command) {
		Objects.requireNonNull(command, "command");
		return builder()
			.withCommandId(UUID.randomUUID().toString())
			.withCommandType(command.getType())
			.withSchemaVersion(CURRENT_SCHEMA_VERSION)
			.withCreatedAt(Instant.now())
			.withCorrelationId(command.getCorrelationId())
			.withPayload(command.toJson())
			.build();
	}

	public static CommandEnvelope fromJson(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return builder()
			.withCommandId(json.getString("command_id"))
			.withCommandType(json.getString("command_type"))
			.withSchemaVersion(json.getInteger("schema_version", CURRENT_SCHEMA_VERSION))
			.withCreatedAt(Instant.parse(json.getString("created_at")))
			.withCorrelationId(json.getString("correlation_id"))
			.withPayload(json.getJsonObject("payload"))
			.build();
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

	public static final class Builder {
		private String commandId;
		private String commandType;
		private Integer schemaVersion;
		private Instant createdAt;
		private String correlationId;
		private JsonObject payload;

		private Builder() {
		}

		public Builder withCommandId(String value) {
			commandId = value;
			return this;
		}

		public Builder withCommandType(String value) {
			commandType = value;
			return this;
		}

		public Builder withSchemaVersion(int value) {
			schemaVersion = value;
			return this;
		}

		public Builder withCreatedAt(Instant value) {
			createdAt = value;
			return this;
		}

		public Builder withCorrelationId(String value) {
			correlationId = value;
			return this;
		}

		public Builder withPayload(JsonObject value) {
			payload = value == null ? null : value.copy();
			return this;
		}

		public CommandEnvelope build() {
			return new CommandEnvelope(
				commandId,
				commandType,
				Objects.requireNonNull(schemaVersion, "schemaVersion"),
				createdAt,
				correlationId,
				payload
			);
		}
	}
}
