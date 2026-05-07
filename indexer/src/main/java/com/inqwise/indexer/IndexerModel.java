package com.inqwise.indexer;

import java.util.Objects;
import java.util.UUID;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerModel {
	private final Integer id;
	private final String uid;
	private final String targetName;
	private final String indexName;
	private final String queueName;
	private final IndexerType type;
	private final IndexerStatus status;

	private IndexerModel(Builder builder) {
		this.id = builder.id;
		this.uid = builder.uid == null ? UUID.randomUUID().toString() : builder.uid;
		this.targetName = builder.targetName;
		this.indexName = builder.indexName;
		this.queueName = builder.queueName;
		this.type = builder.type == null ? IndexerType.INDEX : builder.type;
		this.status = builder.status == null ? IndexerStatus.STARTED : builder.status;
	}

	public IndexerModel(JsonObject json) {
		this.id = json.getInteger("id");
		this.uid = json.getString("uid", UUID.randomUUID().toString());
		this.targetName = json.getString("target_name");
		this.indexName = json.getString("index_name");
		this.queueName = json.getString("queue_name");
		this.type = IndexerType.valueOf(json.getString("type", IndexerType.INDEX.name()));
		this.status = IndexerStatus.valueOf(json.getString("status", IndexerStatus.STARTED.name()));
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("id", id)
			.put("uid", uid)
			.put("target_name", targetName)
			.put("index_name", indexName)
			.put("queue_name", queueName)
			.put("type", type.name())
			.put("status", status.name());
	}

	public Integer getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public String getTargetName() {
		return targetName;
	}

	public String getIndexName() {
		return indexName;
	}

	public String getQueueName() {
		return queueName;
	}

	public IndexerType getType() {
		return type;
	}

	public IndexerStatus getStatus() {
		return status;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private String uid;
		private String targetName;
		private String indexName;
		private String queueName;
		private IndexerType type;
		private IndexerStatus status;

		private Builder() {
		}

		public Builder withId(Integer id) {
			this.id = id;
			return this;
		}

		public Builder withUid(String uid) {
			this.uid = uid;
			return this;
		}

		public Builder withTargetName(String targetName) {
			this.targetName = targetName;
			return this;
		}

		public Builder withIndexName(String indexName) {
			this.indexName = indexName;
			return this;
		}

		public Builder withQueueName(String queueName) {
			this.queueName = queueName;
			return this;
		}

		public Builder withType(IndexerType type) {
			this.type = type;
			return this;
		}

		public Builder withStatus(IndexerStatus status) {
			this.status = status;
			return this;
		}

		public IndexerModel build() {
			Objects.requireNonNull(indexName, "indexName");
			return new IndexerModel(this);
		}
	}
}
