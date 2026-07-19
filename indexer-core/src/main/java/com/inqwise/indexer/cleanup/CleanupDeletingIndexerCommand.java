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

	public static CleanupDeletingIndexerCommand fromJson(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return builder()
			.withIndexerId(json.getInteger("indexer_id"))
			.build();
	}

	public static Builder builder() {
		return new Builder();
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

	public static final class Builder {
		private Integer indexerId;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public CleanupDeletingIndexerCommand build() {
			return new CleanupDeletingIndexerCommand(indexerId);
		}
	}
}
