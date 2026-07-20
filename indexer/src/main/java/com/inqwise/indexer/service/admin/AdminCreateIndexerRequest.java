package com.inqwise.indexer.service.admin;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.provisioning.CreateIndexerProvisioningRequest;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminCreateIndexerRequest {
	public static final class Keys {
		public static final String PREFIX = "prefix";
		public static final String TARGET_ID = "target_id";
		public static final String INDEX_NAME = "index_name";
		public static final String QUEUE_NAME = "queue_name";

		private Keys() {
		}
	}

	private String prefix;
	private Integer targetId;
	private String indexName;
	private String queueName;

	public AdminCreateIndexerRequest() {
	}

	public AdminCreateIndexerRequest(JsonObject json) {
		this.prefix = json.getString(Keys.PREFIX);
		this.targetId = json.getInteger(Keys.TARGET_ID);
		this.indexName = json.getString(Keys.INDEX_NAME);
		this.queueName = json.getString(Keys.QUEUE_NAME);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.PREFIX, prefix)
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName);
	}

	CreateIndexerProvisioningRequest toProvisioningRequest() {
		return CreateIndexerProvisioningRequest.builder()
			.withPrefix(prefix)
			.withTargetId(targetId)
			.withIndexName(indexName)
			.withQueueName(queueName)
			.withRole(IndexerRole.LIVE_WRITER)
			.withIndexOwnership(IndexResourceOwnership.OWNER)
			.withRuntimeState(IndexerRuntimeState.NON_ACTIVE)
			.build();
	}

	public String getPrefix() {
		return prefix;
	}

	public AdminCreateIndexerRequest setPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public AdminCreateIndexerRequest setTargetId(Integer targetId) {
		this.targetId = targetId;
		return this;
	}

	public String getIndexName() {
		return indexName;
	}

	public AdminCreateIndexerRequest setIndexName(String indexName) {
		this.indexName = indexName;
		return this;
	}

	public String getQueueName() {
		return queueName;
	}

	public AdminCreateIndexerRequest setQueueName(String queueName) {
		this.queueName = queueName;
		return this;
	}

	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}

	public static final class Builder {
		private String prefix;
		private Integer targetId;
		private String indexName;
		private String queueName;

		private Builder() {
		}

		public Builder withPrefix(String value) {
			prefix = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public AdminCreateIndexerRequest build() {
			return new AdminCreateIndexerRequest()
				.setPrefix(requireText(prefix, "prefix"))
				.setTargetId(Objects.requireNonNull(targetId, "targetId"))
				.setIndexName(requireText(indexName, "indexName"))
				.setQueueName(requireText(queueName, "queueName"));
		}
	}

}
