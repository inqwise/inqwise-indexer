package com.inqwise.indexer.service.admin;

import java.time.Instant;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminIndexerView {
	public static final class Keys {
		public static final String ID = "id";
		public static final String UID = "uid";
		public static final String TARGET_ID = "target_id";
		public static final String TARGET_NAME = "target_name";
		public static final String INDEX_NAME = "index_name";
		public static final String QUEUE_NAME = "queue_name";
		public static final String TYPE = "type";
		public static final String ROLE = "role";
		public static final String INDEX_OWNERSHIP = "index_ownership";
		public static final String STATUS = "status";
		public static final String PROVISIONING_STATE = "provisioning_state";
		public static final String RUNTIME_STATE = "runtime_state";
		public static final String PUBLICATION_STATE = "publication_state";
		public static final String MUTATION_STATE = "mutation_state";
		public static final String CREATED_AT = "created_at";
		public static final String UPDATED_AT = "updated_at";
		public static final String VERSION = "version";

		private Keys() {
		}
	}

	private Integer id;
	private String uid;
	private Integer targetId;
	private String targetName;
	private String indexName;
	private String queueName;
	private IndexerType type;
	private IndexerRole role;
	private IndexResourceOwnership indexOwnership;
	private IndexerStatus status;
	private IndexerProvisioningState provisioningState;
	private IndexerRuntimeState runtimeState;
	private PublicationState publicationState;
	private MutationState mutationState;
	private Instant createdAt;
	private Instant updatedAt;
	private long version;

	public AdminIndexerView() {
	}

	public AdminIndexerView(JsonObject json) {
		this.id = json.getInteger(Keys.ID);
		this.uid = json.getString(Keys.UID);
		this.targetId = json.getInteger(Keys.TARGET_ID);
		this.targetName = json.getString(Keys.TARGET_NAME);
		this.indexName = json.getString(Keys.INDEX_NAME);
		this.queueName = json.getString(Keys.QUEUE_NAME);
		this.type = enumValue(json, Keys.TYPE, IndexerType.class);
		this.role = enumValue(json, Keys.ROLE, IndexerRole.class);
		this.indexOwnership = enumValue(json, Keys.INDEX_OWNERSHIP, IndexResourceOwnership.class);
		this.status = enumValue(json, Keys.STATUS, IndexerStatus.class);
		this.provisioningState = enumValue(json, Keys.PROVISIONING_STATE, IndexerProvisioningState.class);
		this.runtimeState = enumValue(json, Keys.RUNTIME_STATE, IndexerRuntimeState.class);
		this.publicationState = enumValue(json, Keys.PUBLICATION_STATE, PublicationState.class);
		this.mutationState = enumValue(json, Keys.MUTATION_STATE, MutationState.class);
		this.createdAt = instant(json, Keys.CREATED_AT);
		this.updatedAt = instant(json, Keys.UPDATED_AT);
		this.version = json.getLong(Keys.VERSION, 0L);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.ID, id)
			.put(Keys.UID, uid)
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName)
			.put(Keys.TYPE, name(type))
			.put(Keys.ROLE, name(role))
			.put(Keys.INDEX_OWNERSHIP, name(indexOwnership))
			.put(Keys.STATUS, name(status))
			.put(Keys.PROVISIONING_STATE, name(provisioningState))
			.put(Keys.RUNTIME_STATE, name(runtimeState))
			.put(Keys.PUBLICATION_STATE, name(publicationState))
			.put(Keys.MUTATION_STATE, name(mutationState))
			.put(Keys.CREATED_AT, string(createdAt))
			.put(Keys.UPDATED_AT, string(updatedAt))
			.put(Keys.VERSION, version);
	}

	public static AdminIndexerView from(IndexerRecord record) {
		return new AdminIndexerView()
			.setId(record.id())
			.setUid(record.uid())
			.setTargetId(record.targetId())
			.setTargetName(record.targetName())
			.setIndexName(record.indexName())
			.setQueueName(record.queueName())
			.setType(record.type())
			.setRole(record.role())
			.setIndexOwnership(record.indexOwnership())
			.setStatus(record.status())
			.setProvisioningState(record.provisioningState())
			.setRuntimeState(record.runtimeState())
			.setPublicationState(record.publicationState())
			.setMutationState(record.mutationState())
			.setCreatedAt(record.createdAt())
			.setUpdatedAt(record.updatedAt())
			.setVersion(record.version());
	}

	public Integer getId() {
		return id;
	}

	public AdminIndexerView setId(Integer id) {
		this.id = id;
		return this;
	}

	public String getUid() {
		return uid;
	}

	public AdminIndexerView setUid(String uid) {
		this.uid = uid;
		return this;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public AdminIndexerView setTargetId(Integer targetId) {
		this.targetId = targetId;
		return this;
	}

	public String getTargetName() {
		return targetName;
	}

	public AdminIndexerView setTargetName(String targetName) {
		this.targetName = targetName;
		return this;
	}

	public String getIndexName() {
		return indexName;
	}

	public AdminIndexerView setIndexName(String indexName) {
		this.indexName = indexName;
		return this;
	}

	public String getQueueName() {
		return queueName;
	}

	public AdminIndexerView setQueueName(String queueName) {
		this.queueName = queueName;
		return this;
	}

	public IndexerType getType() {
		return type;
	}

	public AdminIndexerView setType(IndexerType type) {
		this.type = type;
		return this;
	}

	public IndexerRole getRole() {
		return role;
	}

	public AdminIndexerView setRole(IndexerRole role) {
		this.role = role;
		return this;
	}

	public IndexResourceOwnership getIndexOwnership() {
		return indexOwnership;
	}

	public AdminIndexerView setIndexOwnership(IndexResourceOwnership indexOwnership) {
		this.indexOwnership = indexOwnership;
		return this;
	}

	public IndexerStatus getStatus() {
		return status;
	}

	public AdminIndexerView setStatus(IndexerStatus status) {
		this.status = status;
		return this;
	}

	public IndexerProvisioningState getProvisioningState() {
		return provisioningState;
	}

	public AdminIndexerView setProvisioningState(IndexerProvisioningState provisioningState) {
		this.provisioningState = provisioningState;
		return this;
	}

	public IndexerRuntimeState getRuntimeState() {
		return runtimeState;
	}

	public AdminIndexerView setRuntimeState(IndexerRuntimeState runtimeState) {
		this.runtimeState = runtimeState;
		return this;
	}

	public PublicationState getPublicationState() {
		return publicationState;
	}

	public AdminIndexerView setPublicationState(PublicationState publicationState) {
		this.publicationState = publicationState;
		return this;
	}

	public MutationState getMutationState() {
		return mutationState;
	}

	public AdminIndexerView setMutationState(MutationState mutationState) {
		this.mutationState = mutationState;
		return this;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public AdminIndexerView setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
		return this;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public AdminIndexerView setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
		return this;
	}

	public long getVersion() {
		return version;
	}

	public AdminIndexerView setVersion(long version) {
		this.version = version;
		return this;
	}

	private static Instant instant(JsonObject json, String key) {
		String value = json.getString(key);
		return value == null ? null : Instant.parse(value);
	}

	private static String string(Instant value) {
		return value == null ? null : value.toString();
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private static <E extends Enum<E>> E enumValue(JsonObject json, String key, Class<E> type) {
		String value = json.getString(key);
		return value == null ? null : Enum.valueOf(type, value);
	}
}
