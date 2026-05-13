package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class IndexerLifecycleChanged {
	private final Integer indexerId;
	private final IndexerStatus status;
	private final long version;
	private final String commandId;

	public IndexerLifecycleChanged(
		Integer indexerId,
		IndexerStatus status,
		long version,
		String commandId
	) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.status = Objects.requireNonNull(status, "status");
		this.version = version;
		this.commandId = commandId;
	}

	public IndexerLifecycleChanged(JsonObject json) {
		this(
			json.getInteger("indexer_id"),
			IndexerStatus.valueOf(json.getString("status")),
			json.getLong("version", 0L),
			json.getString("command_id")
		);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public IndexerStatus getStatus() {
		return status;
	}

	public long getVersion() {
		return version;
	}

	public String getCommandId() {
		return commandId;
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("status", status.name())
			.put("version", version)
			.put("command_id", commandId);
	}
}
