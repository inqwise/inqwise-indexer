package com.inqwise.indexer.commands;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public class RecoverTargetProvisioningCommand implements Command {
	public static final String TYPE = "target.provisioning.recover";

	private final Integer targetId;
	private final long expectedVersion;

	public RecoverTargetProvisioningCommand(Integer targetId, long expectedVersion) {
		this.targetId = Objects.requireNonNull(targetId, "targetId");
		this.expectedVersion = expectedVersion;
	}

	public RecoverTargetProvisioningCommand(JsonObject json) {
		this(
			json.getInteger("target_id"),
			json.getLong("expected_version", 0L)
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public long getExpectedVersion() {
		return expectedVersion;
	}

	@Override
	public JsonObject toJson() {
		return new JsonObject()
			.put("target_id", targetId)
			.put("expected_version", expectedVersion);
	}
}
