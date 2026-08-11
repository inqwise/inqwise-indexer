package com.inqwise.indexer.service.admin;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.lifecycle.TargetInvalidationEntry;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminTargetInvalidationView {
	public static final class Keys {
		public static final String TARGET_ID = "target_id";
		public static final String VERSION = "version";
		public static final String EXPIRES_AT = "expires_at";

		private Keys() {
		}
	}

	private Integer targetId;
	private long version;
	private Instant expiresAt;

	public AdminTargetInvalidationView() {
	}

	public AdminTargetInvalidationView(JsonObject json) {
		this.targetId = json.getInteger(Keys.TARGET_ID);
		this.version = json.getLong(Keys.VERSION, 0L);
		String expires = json.getString(Keys.EXPIRES_AT);
		this.expiresAt = expires == null ? null : Instant.parse(expires);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static AdminTargetInvalidationView from(TargetInvalidationEntry entry) {
		Objects.requireNonNull(entry, "entry");
		return builder()
			.withTargetId(entry.concreteTargetId())
			.withVersion(entry.version())
			.withExpiresAt(entry.expiresAt())
			.build();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.VERSION, version)
			.put(Keys.EXPIRES_AT, expiresAt == null ? null : expiresAt.toString());
	}

	public static final class Builder {
		private Integer targetId;
		private Long version;
		private Instant expiresAt;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public Builder withExpiresAt(Instant value) {
			expiresAt = value;
			return this;
		}

		public AdminTargetInvalidationView build() {
			AdminTargetInvalidationView view = new AdminTargetInvalidationView();
			view.targetId = Objects.requireNonNull(targetId, "targetId");
			view.version = Objects.requireNonNull(version, "version");
			view.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
			return view;
		}
	}
}
