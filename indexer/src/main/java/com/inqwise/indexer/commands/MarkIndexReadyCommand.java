package com.inqwise.indexer.commands;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class MarkIndexReadyCommand implements Command {
	public static final String TYPE = "index.ready.mark";

	private final Integer publicationId;
	private final String reason;
	private final long expectedVersion;

	public MarkIndexReadyCommand(
		Integer publicationId,
		String reason,
		long expectedVersion
	) {
		this.publicationId = Objects.requireNonNull(publicationId, "publicationId");
		this.reason = reason;
		this.expectedVersion = expectedVersion;
	}

	public MarkIndexReadyCommand(JsonObject json) {
		this(
			json.getInteger("publication_id"),
			json.getString("reason"),
			json.getLong("expected_version")
		);
	}

	@Override
	public String getType() {
		return TYPE;
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
			.put("publication_id", publicationId)
			.put("reason", reason)
			.put("expected_version", expectedVersion);
	}
}
