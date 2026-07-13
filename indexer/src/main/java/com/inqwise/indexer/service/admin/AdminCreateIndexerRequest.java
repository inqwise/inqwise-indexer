package com.inqwise.indexer.service.admin;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.provisioning.CreateIndexerProvisioningRequest;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminCreateIndexerRequest {
	public static final class Keys {
		public static final String PREFIX = "prefix";
		public static final String TARGET_ID = "target_id";
		public static final String TARGET_NAME = "target_name";
		public static final String INDEX_NAME = "index_name";
		public static final String QUEUE_NAME = "queue_name";
		public static final String INDEXER_TYPE = "indexer_type";
		public static final String ROLE = "role";
		public static final String INDEX_OWNERSHIP = "index_ownership";
		public static final String RUNTIME_STATE = "runtime_state";
		public static final String PUBLICATION_STATE = "publication_state";
		public static final String MUTATION_STATE = "mutation_state";

		private Keys() {
		}
	}

	private String prefix;
	private Integer targetId;
	private String targetName;
	private String indexName;
	private String queueName;
	private IndexerType indexerType = IndexerType.INDEX;
	private IndexerRole role = IndexerRole.LIVE_WRITER;
	private IndexResourceOwnership indexOwnership = IndexResourceOwnership.OWNER;
	private IndexerRuntimeState runtimeState = IndexerRuntimeState.NON_ACTIVE;
	private PublicationState publicationState = PublicationState.UNPUBLISHED;
	private MutationState mutationState = MutationState.WRITABLE;

	public AdminCreateIndexerRequest() {
	}

	public AdminCreateIndexerRequest(JsonObject json) {
		this.prefix = json.getString(Keys.PREFIX);
		this.targetId = json.getInteger(Keys.TARGET_ID);
		this.targetName = json.getString(Keys.TARGET_NAME);
		this.indexName = json.getString(Keys.INDEX_NAME);
		this.queueName = json.getString(Keys.QUEUE_NAME);
		this.indexerType = IndexerType.valueOf(json.getString(Keys.INDEXER_TYPE, IndexerType.INDEX.name()));
		this.role = IndexerRole.valueOf(json.getString(Keys.ROLE, IndexerRole.LIVE_WRITER.name()));
		this.indexOwnership = IndexResourceOwnership.valueOf(json.getString(
			Keys.INDEX_OWNERSHIP,
			IndexResourceOwnership.OWNER.name()
		));
		this.runtimeState = IndexerRuntimeState.valueOf(json.getString(
			Keys.RUNTIME_STATE,
			IndexerRuntimeState.NON_ACTIVE.name()
		));
		this.publicationState = PublicationState.valueOf(json.getString(
			Keys.PUBLICATION_STATE,
			PublicationState.UNPUBLISHED.name()
		));
		this.mutationState = MutationState.valueOf(json.getString(
			Keys.MUTATION_STATE,
			MutationState.WRITABLE.name()
		));
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.PREFIX, prefix)
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName)
			.put(Keys.INDEXER_TYPE, indexerType.name())
			.put(Keys.ROLE, role.name())
			.put(Keys.INDEX_OWNERSHIP, indexOwnership.name())
			.put(Keys.RUNTIME_STATE, runtimeState.name())
			.put(Keys.PUBLICATION_STATE, publicationState.name())
			.put(Keys.MUTATION_STATE, mutationState.name());
	}

	CreateIndexerProvisioningRequest toProvisioningRequest() {
		return new CreateIndexerProvisioningRequest(
			prefix,
			targetId,
			targetName,
			indexName,
			queueName,
			indexerType,
			role,
			indexOwnership,
			runtimeState,
			publicationState,
			mutationState
		);
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

	public String getTargetName() {
		return targetName;
	}

	public AdminCreateIndexerRequest setTargetName(String targetName) {
		this.targetName = targetName;
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

	public IndexerType getIndexerType() {
		return indexerType;
	}

	public AdminCreateIndexerRequest setIndexerType(IndexerType indexerType) {
		this.indexerType = indexerType == null ? IndexerType.INDEX : indexerType;
		return this;
	}

	public IndexerRole getRole() {
		return role;
	}

	public AdminCreateIndexerRequest setRole(IndexerRole role) {
		this.role = role == null ? IndexerRole.LIVE_WRITER : role;
		return this;
	}

	public IndexResourceOwnership getIndexOwnership() {
		return indexOwnership;
	}

	public AdminCreateIndexerRequest setIndexOwnership(IndexResourceOwnership indexOwnership) {
		this.indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
		return this;
	}

	public IndexerRuntimeState getRuntimeState() {
		return runtimeState;
	}

	public AdminCreateIndexerRequest setRuntimeState(IndexerRuntimeState runtimeState) {
		this.runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
		return this;
	}

	public PublicationState getPublicationState() {
		return publicationState;
	}

	public AdminCreateIndexerRequest setPublicationState(PublicationState publicationState) {
		this.publicationState = publicationState == null ? PublicationState.UNPUBLISHED : publicationState;
		return this;
	}

	public MutationState getMutationState() {
		return mutationState;
	}

	public AdminCreateIndexerRequest setMutationState(MutationState mutationState) {
		this.mutationState = mutationState == null ? MutationState.WRITABLE : mutationState;
		return this;
	}
}
