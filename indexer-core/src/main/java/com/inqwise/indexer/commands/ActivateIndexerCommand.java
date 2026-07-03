package com.inqwise.indexer.commands;

import java.util.Objects;
import io.vertx.core.json.JsonObject;

public class ActivateIndexerCommand implements Command {
	public static final String TYPE = "indexer.activate";

	private final Integer indexerId;
	private final long expectedVersion;

	public ActivateIndexerCommand(Integer indexerId, long expectedVersion) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.expectedVersion = expectedVersion;
	}

	public ActivateIndexerCommand(JsonObject json) {
		this(json.getInteger("indexer_id"), json.getLong("expected_version"));
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public long getExpectedVersion() {
		return expectedVersion;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("expected_version", expectedVersion);
	}
}
