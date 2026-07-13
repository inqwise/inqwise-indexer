package com.inqwise.indexer.cleanup;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public final class CleanupDeletingIndexerCommand implements Command {
	public static final String TYPE = "indexer.cleanup-deleting";

	private final Integer indexerId;

	public CleanupDeletingIndexerCommand(Integer indexerId) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
	}

	public CleanupDeletingIndexerCommand(JsonObject json) {
		this(json.getInteger("indexer_id"));
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId);
	}
}
