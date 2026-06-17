package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public class StartLoadCommand implements Command {
	public static final String TYPE = "indexer.load.start";

	private final Integer indexerId;
	private final long expectedLoadVersion;

	public StartLoadCommand(Integer indexerId, long expectedLoadVersion) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.expectedLoadVersion = expectedLoadVersion;
	}

	public StartLoadCommand(JsonObject json) {
		this(
			json.getInteger("indexer_id"),
			json.getLong("expected_load_version")
		);
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
