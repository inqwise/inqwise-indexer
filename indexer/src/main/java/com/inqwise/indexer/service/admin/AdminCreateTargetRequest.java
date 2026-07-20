package com.inqwise.indexer.service.admin;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.targets.CreateTargetRequest;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminCreateTargetRequest {
	public static final class Keys {
		public static final String TARGET_NAME = "target_name";
		public static final String TIMESTAMP = "timestamp";
		public static final String CREATE_INDEXER = "create_indexer";

		private Keys() {
		}
	}

	private String targetName;
	private Instant timestamp;
	private AdminCreateTargetIndexerRequest createIndexer;

	public AdminCreateTargetRequest() {
	}

	public AdminCreateTargetRequest(JsonObject json) {
		this.targetName = json.getString(Keys.TARGET_NAME);
		this.timestamp = json.getString(Keys.TIMESTAMP) == null
			? null
			: Instant.parse(json.getString(Keys.TIMESTAMP));
		JsonObject createIndexerJson = json.getJsonObject(Keys.CREATE_INDEXER);
		this.createIndexer = createIndexerJson == null
			? null
			: new AdminCreateTargetIndexerRequest(createIndexerJson);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.TIMESTAMP, timestamp == null ? null : timestamp.toString())
			.put(Keys.CREATE_INDEXER, createIndexer == null ? null : createIndexer.toJson());
	}

	CreateTargetRequest toTargetRequest() {
		return CreateTargetRequest.builder()
			.withTargetName(targetName)
			.withTimestamp(timestamp)
			.withCreateIndexer(createIndexer == null ? null : createIndexer.toTargetRequest())
			.build();
	}

	public String getTargetName() {
		return targetName;
	}

	public AdminCreateTargetRequest setTargetName(String targetName) {
		this.targetName = targetName;
		return this;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public AdminCreateTargetRequest setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
		return this;
	}

	public AdminCreateTargetIndexerRequest getCreateIndexer() {
		return createIndexer;
	}

	public AdminCreateTargetRequest setCreateIndexer(AdminCreateTargetIndexerRequest createIndexer) {
		this.createIndexer = createIndexer;
		return this;
	}

	public static final class Builder {
		private String targetName;
		private Instant timestamp;
		private AdminCreateTargetIndexerRequest createIndexer;

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

		public Builder withCreateIndexer(AdminCreateTargetIndexerRequest value) {
			createIndexer = value;
			return this;
		}

		public AdminCreateTargetRequest build() {
			Objects.requireNonNull(targetName, "targetName");
			if (targetName.isBlank()) {
				throw new IllegalArgumentException("targetName must not be blank");
			}
			return new AdminCreateTargetRequest()
				.setTargetName(targetName)
				.setTimestamp(timestamp)
				.setCreateIndexer(createIndexer);
		}
	}
}
