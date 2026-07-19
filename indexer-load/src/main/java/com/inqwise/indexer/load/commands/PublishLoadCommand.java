package com.inqwise.indexer.load.commands;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public class PublishLoadCommand implements Command {
	public static final String TYPE = "indexer.load.publish";

	private final Integer indexerId;
	private final long expectedLoadVersion;

	public PublishLoadCommand(Integer indexerId, long expectedLoadVersion) {
		this(builder()
			.withIndexerId(indexerId)
			.withExpectedLoadVersion(expectedLoadVersion));
	}

	public PublishLoadCommand(JsonObject json) {
		this(builder(json));
	}

	private PublishLoadCommand(Builder builder) {
		indexerId = Objects.requireNonNull(builder.indexerId, "indexerId");
		expectedLoadVersion = Objects.requireNonNull(
			builder.expectedLoadVersion,
			"expectedLoadVersion"
		);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder builder(JsonObject json) {
		Objects.requireNonNull(json, "json");
		Builder builder = builder().withIndexerId(json.getInteger("indexer_id"));
		Long expectedLoadVersion = json.getLong("expected_load_version");
		if (expectedLoadVersion != null) {
			builder.withExpectedLoadVersion(expectedLoadVersion);
		}
		return builder;
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public long getExpectedLoadVersion() {
		return expectedLoadVersion;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("expected_load_version", expectedLoadVersion);
	}

	public static final class Builder {
		private Integer indexerId;
		private Long expectedLoadVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withExpectedLoadVersion(long value) {
			expectedLoadVersion = value;
			return this;
		}

		public PublishLoadCommand build() {
			return new PublishLoadCommand(this);
		}
	}
}
