package com.inqwise.indexer.load.commands;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public class PublishLoadCommand implements Command {
	public static final String TYPE = "indexer.load.publish";

	private final Integer indexerId;
	private final long expectedLoadVersion;

	public PublishLoadCommand(Integer indexerId, long expectedLoadVersion) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.expectedLoadVersion = expectedLoadVersion;
	}

	public PublishLoadCommand(JsonObject json) {
		this(json.getInteger("indexer_id"), json.getLong("expected_load_version"));
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
}
