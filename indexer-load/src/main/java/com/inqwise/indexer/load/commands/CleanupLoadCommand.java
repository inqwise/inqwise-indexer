package com.inqwise.indexer.load.commands;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public final class CleanupLoadCommand implements Command {
	public static final String TYPE = "indexer.load.cleanup";

	private final Integer indexerId;
	private final Integer oldPublishedIndexerId;

	public CleanupLoadCommand(Integer indexerId, Integer oldPublishedIndexerId) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.oldPublishedIndexerId = oldPublishedIndexerId;
	}

	public CleanupLoadCommand(JsonObject json) {
		this(json.getInteger("indexer_id"), json.getInteger("old_published_indexer_id"));
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public Integer getOldPublishedIndexerId() {
		return oldPublishedIndexerId;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject().put("indexer_id", indexerId);
		if (oldPublishedIndexerId != null) {
			json.put("old_published_indexer_id", oldPublishedIndexerId);
		}
		return json;
	}
}
