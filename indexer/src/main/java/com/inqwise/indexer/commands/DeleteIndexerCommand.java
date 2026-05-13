package com.inqwise.indexer.commands;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class DeleteIndexerCommand implements Command {
	public static final String TYPE = "indexer.delete";

	private final Integer indexerId;

	public DeleteIndexerCommand(Integer indexerId) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
	}

	public DeleteIndexerCommand(JsonObject json) {
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
