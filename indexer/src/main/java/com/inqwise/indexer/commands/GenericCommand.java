package com.inqwise.indexer.commands;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public final class GenericCommand implements Command {
	private final String type;
	private final String correlationId;
	private final JsonObject payload;

	public GenericCommand(String type, JsonObject payload) {
		this(type, null, payload);
	}

	public GenericCommand(String type, String correlationId, JsonObject payload) {
		this.type = requireText(type, "type");
		this.correlationId = correlationId == null
			? null
			: requireText(correlationId, "correlationId");
		this.payload = Objects.requireNonNull(payload, "payload").copy();
	}

	@Override
	public String getType() {
		return type;
	}

	@Override
	public String getCorrelationId() {
		return correlationId;
	}

	@Override
	public JsonObject toJson() {
		return payload.copy();
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " cannot be blank");
		}
		return value;
	}
}
