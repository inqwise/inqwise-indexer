package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerModel {
	private final Integer id;
	private final String uid;
	private final Integer targetId;
	private final String targetName;
	private final String indexName;
	private final String queueName;
	private final IndexerType type;
	private final IndexerRuntimeState runtimeState;
	private final long version;

	private IndexerModel(Builder builder) {
		this.id = builder.id;
		this.uid = builder.uid;
		this.targetId = builder.targetId;
		this.targetName = builder.targetName;
		this.indexName = builder.indexName;
		this.queueName = builder.queueName;
		this.type = builder.type == null ? IndexerType.INDEX : builder.type;
		this.runtimeState = builder.runtimeState == null ? IndexerRuntimeState.ACTIVE : builder.runtimeState;
		this.version = builder.version;
	}

	public IndexerModel(JsonObject json) {
		this.id = json.getInteger("id");
		this.uid = json.getString("uid");
		this.targetId = json.getInteger("target_id");
		this.targetName = json.getString("target_name");
		this.indexName = json.getString("index_name");
		this.queueName = json.getString("queue_name");
		this.type = IndexerType.valueOf(json.getString("type", IndexerType.INDEX.name()));
		this.runtimeState = IndexerRuntimeState.valueOf(
			json.getString("runtime_state", IndexerRuntimeState.ACTIVE.name())
		);
		this.version = json.getLong("version", 0L);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("id", id)
			.put("uid", uid)
			.put("target_id", targetId)
			.put("target_name", targetName)
			.put("index_name", indexName)
			.put("queue_name", queueName)
			.put("type", type.name())
			.put("runtime_state", runtimeState.name())
			.put("version", version);
	}

	public Integer getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public Integer getTargetId() {
		return targetId;
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

	public IndexerRuntimeState getRuntimeState() {
		return runtimeState;
	}

	public long getVersion() {
		return version;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private String uid;
		private Integer targetId;
		private String targetName;
		private String indexName;
		private String queueName;
		private IndexerType type;
		private IndexerRuntimeState runtimeState;
		private long version;

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

		public Builder withTargetId(Integer targetId) {
			this.targetId = targetId;
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

		public Builder withRuntimeState(IndexerRuntimeState runtimeState) {
			this.runtimeState = runtimeState;
			return this;
		}

		public Builder withVersion(long version) {
			this.version = version;
			return this;
		}

		public IndexerModel build() {
			Objects.requireNonNull(indexName, "indexName");
			return new IndexerModel(this);
		}
	}
}
