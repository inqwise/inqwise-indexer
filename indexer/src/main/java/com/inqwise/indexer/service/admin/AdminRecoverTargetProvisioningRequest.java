package com.inqwise.indexer.service.admin;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminRecoverTargetProvisioningRequest {
	public static final class Keys {
		public static final String TARGET_ID = "target_id";
		public static final String EXPECTED_VERSION = "expected_version";

		private Keys() {
		}
	}

	private Integer targetId;
	private long expectedVersion;

	public AdminRecoverTargetProvisioningRequest() {
	}

	public AdminRecoverTargetProvisioningRequest(JsonObject json) {
		this.targetId = json.getInteger(Keys.TARGET_ID);
		this.expectedVersion = json.getLong(Keys.EXPECTED_VERSION, 0L);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.EXPECTED_VERSION, expectedVersion);
	}

	public Integer getTargetId() {
		return targetId;
	}

	public AdminRecoverTargetProvisioningRequest setTargetId(Integer targetId) {
		this.targetId = targetId;
		return this;
	}

	public long getExpectedVersion() {
		return expectedVersion;
	}

	public AdminRecoverTargetProvisioningRequest setExpectedVersion(long expectedVersion) {
		this.expectedVersion = expectedVersion;
		return this;
	}

	public static final class Builder {
		private Integer targetId;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public AdminRecoverTargetProvisioningRequest build() {
			return new AdminRecoverTargetProvisioningRequest()
				.setTargetId(Objects.requireNonNull(targetId, "targetId"))
				.setExpectedVersion(Objects.requireNonNull(expectedVersion, "expectedVersion"));
		}
	}
}
