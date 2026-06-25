package com.inqwise.indexer.commands;

import java.util.Objects;
import io.vertx.core.json.JsonObject;

public class DeactivateIndexerCommand implements Command {
	public static final String TYPE = "indexer.deactivate";

	private final Integer indexerId;

	public DeactivateIndexerCommand(Integer indexerId) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
	}

	public DeactivateIndexerCommand(JsonObject json) {
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
