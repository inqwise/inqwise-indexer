package com.inqwise.indexer.commands;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class DeleteIndexerCommand implements Command {
	public static final String TYPE = "indexer.delete";

	private final Integer indexerId;
	private final Long expectedVersion;

	public DeleteIndexerCommand(Integer indexerId) {
		this(indexerId, null);
	}

	public DeleteIndexerCommand(Integer indexerId, Long expectedVersion) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.expectedVersion = expectedVersion;
	}

	public DeleteIndexerCommand(JsonObject json) {
		this(json.getInteger("indexer_id"), json.getLong("expected_version"));
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put("indexer_id", indexerId);

		if (expectedVersion != null) {
			json.put("expected_version", expectedVersion);
		}

		return json;
	}
}
