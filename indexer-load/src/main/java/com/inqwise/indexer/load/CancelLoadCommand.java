package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public class CancelLoadCommand implements Command {
	public static final String TYPE = "indexer.load.cancel";

	private final Integer indexerId;
	private final String reason;
	private final long expectedLoadVersion;

	public CancelLoadCommand(Integer indexerId, String reason, long expectedLoadVersion) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.reason = reason;
		this.expectedLoadVersion = expectedLoadVersion;
	}

	public CancelLoadCommand(JsonObject json) {
		this(
			json.getInteger("indexer_id"),
			json.getString("reason"),
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

	public String getReason() {
		return reason;
	}

	public long getExpectedLoadVersion() {
		return expectedLoadVersion;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put("indexer_id", indexerId)
			.put("expected_load_version", expectedLoadVersion);

		if (reason != null) {
			json.put("reason", reason);
		}

		return json;
	}
}
