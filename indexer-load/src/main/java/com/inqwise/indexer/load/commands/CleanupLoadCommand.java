package com.inqwise.indexer.load.commands;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public final class CleanupLoadCommand implements Command {
	public static final String TYPE = "indexer.load.cleanup";

	private final Integer indexerId;
	private final Integer oldPublishedIndexerId;

	public CleanupLoadCommand(Integer indexerId, Integer oldPublishedIndexerId) {
		this(builder()
			.withIndexerId(indexerId)
			.withOldPublishedIndexerId(oldPublishedIndexerId));
	}

	public CleanupLoadCommand(JsonObject json) {
		this(builder(json));
	}

	private CleanupLoadCommand(Builder builder) {
		indexerId = Objects.requireNonNull(builder.indexerId, "indexerId");
		oldPublishedIndexerId = builder.oldPublishedIndexerId;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return builder()
			.withIndexerId(json.getInteger("indexer_id"))
			.withOldPublishedIndexerId(json.getInteger("old_published_indexer_id"));
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

	public static final class Builder {
		private Integer indexerId;
		private Integer oldPublishedIndexerId;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withOldPublishedIndexerId(Integer value) {
			oldPublishedIndexerId = value;
			return this;
		}

		public CleanupLoadCommand build() {
			return new CleanupLoadCommand(this);
		}
	}
}
