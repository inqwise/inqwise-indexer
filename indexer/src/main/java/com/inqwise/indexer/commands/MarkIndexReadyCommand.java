package com.inqwise.indexer.commands;

import java.util.Objects;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class MarkIndexReadyCommand implements Command {
	public static final String TYPE = "index.ready.mark";

	private final String commandId;
	private final Integer publicationId;
	private final String reason;
	private final long expectedVersion;

	public MarkIndexReadyCommand(
		Integer publicationId,
		String reason,
		long expectedVersion
	) {
		this(UUID.randomUUID().toString(), publicationId, reason, expectedVersion);
	}

	public MarkIndexReadyCommand(
		String commandId,
		Integer publicationId,
		String reason,
		long expectedVersion
	) {
		this.commandId = Objects.requireNonNull(commandId, "commandId");
		this.publicationId = Objects.requireNonNull(publicationId, "publicationId");
		this.reason = reason;
		this.expectedVersion = expectedVersion;
	}

	public MarkIndexReadyCommand(JsonObject json) {
		this(
			json.getString("command_id"),
			json.getInteger("publication_id"),
			json.getString("reason"),
			json.getLong("expected_version")
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public String getCommandId() {
		return commandId;
	}

	public Integer getPublicationId() {
		return publicationId;
	}

	public String getReason() {
		return reason;
	}

	public long getExpectedVersion() {
		return expectedVersion;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("command_id", commandId)
			.put("publication_id", publicationId)
			.put("reason", reason)
			.put("expected_version", expectedVersion);
	}
}
