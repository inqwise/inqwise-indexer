package com.inqwise.indexer.service.target;

import java.time.Instant;

import com.inqwise.indexer.catalog.targets.InitialPublicationMode;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetCreateRequest {
	private String targetName;
	private Instant timestamp;
	private InitialPublicationMode initialPublicationMode;

	public TargetCreateRequest() {
	}

	public TargetCreateRequest(JsonObject json) {
		targetName = json.getString("target_name");
		String timestampValue = json.getString("timestamp");
		timestamp = timestampValue == null ? null : Instant.parse(timestampValue);
		String mode = json.getString("initial_publication_mode");
		initialPublicationMode = mode == null ? null : InitialPublicationMode.valueOf(mode);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("target_name", targetName)
			.put("timestamp", timestamp == null ? null : timestamp.toString())
			.put(
				"initial_publication_mode",
				initialPublicationMode == null ? null : initialPublicationMode.name()
			);
	}

	public String getTargetName() {
		return targetName;
	}

	public TargetCreateRequest setTargetName(String value) {
		targetName = value;
		return this;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public TargetCreateRequest setTimestamp(Instant value) {
		timestamp = value;
		return this;
	}

	public InitialPublicationMode getInitialPublicationMode() {
		return initialPublicationMode;
	}

	public TargetCreateRequest setInitialPublicationMode(InitialPublicationMode value) {
		initialPublicationMode = value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private Instant timestamp;
		private InitialPublicationMode initialPublicationMode;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withTimestamp(Instant value) {
			timestamp = value;
			return this;
		}

		public Builder withInitialPublicationMode(InitialPublicationMode value) {
			initialPublicationMode = value;
			return this;
		}

		public TargetCreateRequest build() {
			if (targetName == null || targetName.isBlank()) {
				throw new IllegalArgumentException("targetName is required");
			}
			return new TargetCreateRequest()
				.setTargetName(targetName)
				.setTimestamp(timestamp)
				.setInitialPublicationMode(initialPublicationMode);
		}
	}
}
