package com.inqwise.indexer.service.runtime;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.runtime.IndexerSnapshot;
import com.inqwise.indexer.catalog.indexers.IndexerType;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class RuntimeIndexerStatus {
	public static final class Keys {
		public static final String INDEXER_ID = "indexer_id";
		public static final String INDEXER_UID = "indexer_uid";
		public static final String TARGET_ID = "target_id";
		public static final String TARGET_NAME = "target_name";
		public static final String INDEX_NAME = "index_name";
		public static final String QUEUE_NAME = "queue_name";
		public static final String TYPE = "type";
		public static final String ROLE = "role";
		public static final String INDEX_OWNERSHIP = "index_ownership";
		public static final String RUNTIME_STATE = "runtime_state";
		public static final String VERSION = "version";

		private Keys() {
		}
	}

	private Integer indexerId;
	private String indexerUid;
	private Integer targetId;
	private String targetName;
	private String indexName;
	private String queueName;
	private IndexerType type;
	private IndexerRole role;
	private IndexResourceOwnership indexOwnership;
	private IndexerRuntimeState runtimeState;
	private long version;

	public RuntimeIndexerStatus() {
	}

	public RuntimeIndexerStatus(JsonObject json) {
		this.indexerId = json.getInteger(Keys.INDEXER_ID);
		this.indexerUid = json.getString(Keys.INDEXER_UID);
		this.targetId = json.getInteger(Keys.TARGET_ID);
		this.targetName = json.getString(Keys.TARGET_NAME);
		this.indexName = json.getString(Keys.INDEX_NAME);
		this.queueName = json.getString(Keys.QUEUE_NAME);
		this.type = IndexerType.valueOf(json.getString(Keys.TYPE, IndexerType.INDEX.name()));
		this.role = IndexerRole.valueOf(json.getString(Keys.ROLE, IndexerRole.LIVE_WRITER.name()));
		this.indexOwnership = IndexResourceOwnership.valueOf(
			json.getString(Keys.INDEX_OWNERSHIP, IndexResourceOwnership.OWNER.name())
		);
		this.runtimeState = IndexerRuntimeState.valueOf(
			json.getString(Keys.RUNTIME_STATE, IndexerRuntimeState.ACTIVE.name())
		);
		this.version = json.getLong(Keys.VERSION, 0L);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.INDEXER_ID, indexerId)
			.put(Keys.INDEXER_UID, indexerUid)
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName)
			.put(Keys.TYPE, type == null ? null : type.name())
			.put(Keys.ROLE, role == null ? null : role.name())
			.put(Keys.INDEX_OWNERSHIP, indexOwnership == null ? null : indexOwnership.name())
			.put(Keys.RUNTIME_STATE, runtimeState == null ? null : runtimeState.name())
			.put(Keys.VERSION, version);
	}

	public static RuntimeIndexerStatus from(IndexerSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		IndexerModel model = snapshot.getModel();
		return builder()
			.withIndexerId(model.getId())
			.withIndexerUid(model.getUid())
			.withTargetId(model.getTargetId())
			.withTargetName(model.getTargetName())
			.withIndexName(model.getIndexName())
			.withQueueName(snapshot.getQueueName())
			.withType(model.getType())
			.withRole(model.getRole())
			.withIndexOwnership(model.getIndexOwnership())
			.withRuntimeState(model.getRuntimeState())
			.withVersion(model.getVersion())
			.build();
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public RuntimeIndexerStatus setIndexerId(Integer indexerId) {
		this.indexerId = indexerId;
		return this;
	}

	public String getIndexerUid() {
		return indexerUid;
	}

	public RuntimeIndexerStatus setIndexerUid(String indexerUid) {
		this.indexerUid = indexerUid;
		return this;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public RuntimeIndexerStatus setTargetId(Integer targetId) {
		this.targetId = targetId;
		return this;
	}

	public String getTargetName() {
		return targetName;
	}

	public RuntimeIndexerStatus setTargetName(String targetName) {
		this.targetName = targetName;
		return this;
	}

	public String getIndexName() {
		return indexName;
	}

	public RuntimeIndexerStatus setIndexName(String indexName) {
		this.indexName = indexName;
		return this;
	}

	public String getQueueName() {
		return queueName;
	}

	public RuntimeIndexerStatus setQueueName(String queueName) {
		this.queueName = queueName;
		return this;
	}

	public IndexerType getType() {
		return type;
	}

	public RuntimeIndexerStatus setType(IndexerType type) {
		this.type = type;
		return this;
	}

	public IndexerRole getRole() {
		return role;
	}

	public RuntimeIndexerStatus setRole(IndexerRole role) {
		this.role = role;
		return this;
	}

	public IndexResourceOwnership getIndexOwnership() {
		return indexOwnership;
	}

	public RuntimeIndexerStatus setIndexOwnership(IndexResourceOwnership indexOwnership) {
		this.indexOwnership = indexOwnership;
		return this;
	}

	public IndexerRuntimeState getRuntimeState() {
		return runtimeState;
	}

	public RuntimeIndexerStatus setRuntimeState(IndexerRuntimeState runtimeState) {
		this.runtimeState = runtimeState;
		return this;
	}

	public long getVersion() {
		return version;
	}

	public RuntimeIndexerStatus setVersion(long version) {
		this.version = version;
		return this;
	}

	public static final class Builder {
		private Integer indexerId;
		private String indexerUid;
		private Integer targetId;
		private String targetName;
		private String indexName;
		private String queueName;
		private IndexerType type;
		private IndexerRole role;
		private IndexResourceOwnership indexOwnership;
		private IndexerRuntimeState runtimeState;
		private Long version;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withIndexerUid(String value) {
			indexerUid = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
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

		public Builder withType(IndexerType value) {
			type = value;
			return this;
		}

		public Builder withRole(IndexerRole value) {
			role = value;
			return this;
		}

		public Builder withIndexOwnership(IndexResourceOwnership value) {
			indexOwnership = value;
			return this;
		}

		public Builder withRuntimeState(IndexerRuntimeState value) {
			runtimeState = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public RuntimeIndexerStatus build() {
			return new RuntimeIndexerStatus()
				.setIndexerId(Objects.requireNonNull(indexerId, "indexerId"))
				.setIndexerUid(indexerUid)
				.setTargetId(Objects.requireNonNull(targetId, "targetId"))
				.setTargetName(Objects.requireNonNull(targetName, "targetName"))
				.setIndexName(Objects.requireNonNull(indexName, "indexName"))
				.setQueueName(Objects.requireNonNull(queueName, "queueName"))
				.setType(Objects.requireNonNull(type, "type"))
				.setRole(Objects.requireNonNull(role, "role"))
				.setIndexOwnership(Objects.requireNonNull(indexOwnership, "indexOwnership"))
				.setRuntimeState(Objects.requireNonNull(runtimeState, "runtimeState"))
				.setVersion(Objects.requireNonNull(version, "version"));
		}
	}
}
