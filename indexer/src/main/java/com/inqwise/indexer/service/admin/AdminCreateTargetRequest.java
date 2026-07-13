package com.inqwise.indexer.service.admin;

import java.time.Instant;

import com.inqwise.indexer.catalog.targets.CreateTargetRequest;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminCreateTargetRequest {
	public static final class Keys {
		public static final String PREFIX = "prefix";
		public static final String TARGET_NAME = "target_name";
		public static final String TIMESTAMP = "timestamp";
		public static final String CREATE_INDEXER = "create_indexer";

		private Keys() {
		}
	}

	private String prefix;
	private String targetName;
	private Instant timestamp;
	private AdminCreateTargetIndexerRequest createIndexer;

	public AdminCreateTargetRequest() {
	}

	public AdminCreateTargetRequest(JsonObject json) {
		this.prefix = json.getString(Keys.PREFIX);
		this.targetName = json.getString(Keys.TARGET_NAME);
		this.timestamp = json.getString(Keys.TIMESTAMP) == null
			? null
			: Instant.parse(json.getString(Keys.TIMESTAMP));
		JsonObject createIndexerJson = json.getJsonObject(Keys.CREATE_INDEXER);
		this.createIndexer = createIndexerJson == null
			? null
			: new AdminCreateTargetIndexerRequest(createIndexerJson);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.PREFIX, prefix)
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.TIMESTAMP, timestamp == null ? null : timestamp.toString())
			.put(Keys.CREATE_INDEXER, createIndexer == null ? null : createIndexer.toJson());
	}

	CreateTargetRequest toTargetRequest() {
		return new CreateTargetRequest(
			prefix,
			targetName,
			timestamp,
			createIndexer == null ? null : createIndexer.toTargetRequest()
		);
	}

	public String getPrefix() {
		return prefix;
	}

	public AdminCreateTargetRequest setPrefix(String prefix) {
		this.prefix = prefix;
		return this;
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
}
