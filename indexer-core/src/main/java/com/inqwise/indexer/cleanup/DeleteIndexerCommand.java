package com.inqwise.indexer.cleanup;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

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

	public static DeleteIndexerCommand fromJson(JsonObject json) {
		Objects.requireNonNull(json, "json");
		return builder()
			.withIndexerId(json.getInteger("indexer_id"))
			.withExpectedVersion(json.getLong("expected_version"))
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

	public static final class Builder {
		private Integer indexerId;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withExpectedVersion(Long value) {
			expectedVersion = value;
			return this;
		}

		public DeleteIndexerCommand build() {
			return new DeleteIndexerCommand(indexerId, expectedVersion);
		}
	}
}
