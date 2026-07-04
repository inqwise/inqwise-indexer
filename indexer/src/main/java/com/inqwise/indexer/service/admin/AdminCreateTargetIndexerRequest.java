package com.inqwise.indexer.service.admin;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.commands.InitialPublicationMode;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.management.targets.CreateTargetIndexerRequest;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminCreateTargetIndexerRequest {
	public static final class Keys {
		public static final String PREFIX = "prefix";
		public static final String INDEX_NAME = "index_name";
		public static final String QUEUE_NAME = "queue_name";
		public static final String INDEXER_TYPE = "indexer_type";
		public static final String ROLE = "role";
		public static final String INDEX_OWNERSHIP = "index_ownership";
		public static final String RUNTIME_STATE = "runtime_state";
		public static final String PUBLICATION_STATE = "publication_state";
		public static final String MUTATION_STATE = "mutation_state";
		public static final String INITIAL_PUBLICATION_MODE = "initial_publication_mode";

		private Keys() {
		}
	}

	private String prefix;
	private String indexName;
	private String queueName;
	private IndexerType indexerType = IndexerType.INDEX;
	private IndexerRole role = IndexerRole.LIVE_WRITER;
	private IndexResourceOwnership indexOwnership = IndexResourceOwnership.OWNER;
	private IndexerRuntimeState runtimeState = IndexerRuntimeState.NON_ACTIVE;
	private PublicationState publicationState = PublicationState.UNPUBLISHED;
	private MutationState mutationState = MutationState.WRITABLE;
	private InitialPublicationMode initialPublicationMode;

	public AdminCreateTargetIndexerRequest() {
	}

	public AdminCreateTargetIndexerRequest(JsonObject json) {
		this.prefix = json.getString(Keys.PREFIX);
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
		String mode = json.getString(Keys.INITIAL_PUBLICATION_MODE);
		this.initialPublicationMode = mode == null ? null : InitialPublicationMode.valueOf(mode);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.PREFIX, prefix)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName)
			.put(Keys.INDEXER_TYPE, indexerType.name())
			.put(Keys.ROLE, role.name())
			.put(Keys.INDEX_OWNERSHIP, indexOwnership.name())
			.put(Keys.RUNTIME_STATE, runtimeState.name())
			.put(Keys.PUBLICATION_STATE, publicationState.name())
			.put(Keys.MUTATION_STATE, mutationState.name())
			.put(Keys.INITIAL_PUBLICATION_MODE, initialPublicationMode == null
				? null
				: initialPublicationMode.name());
	}

	CreateTargetIndexerRequest toTargetRequest() {
		return new CreateTargetIndexerRequest(
			prefix,
			indexName,
			queueName,
			indexerType,
			role,
			indexOwnership,
			runtimeState,
			publicationState,
			mutationState,
			initialPublicationMode
		);
	}

	public String getPrefix() {
		return prefix;
	}

	public AdminCreateTargetIndexerRequest setPrefix(String prefix) {
		this.prefix = prefix;
		return this;
	}

	public String getIndexName() {
		return indexName;
	}

	public AdminCreateTargetIndexerRequest setIndexName(String indexName) {
		this.indexName = indexName;
		return this;
	}

	public String getQueueName() {
		return queueName;
	}

	public AdminCreateTargetIndexerRequest setQueueName(String queueName) {
		this.queueName = queueName;
		return this;
	}

	public IndexerType getIndexerType() {
		return indexerType;
	}

	public AdminCreateTargetIndexerRequest setIndexerType(IndexerType indexerType) {
		this.indexerType = indexerType == null ? IndexerType.INDEX : indexerType;
		return this;
	}

	public IndexerRole getRole() {
		return role;
	}

	public AdminCreateTargetIndexerRequest setRole(IndexerRole role) {
		this.role = role == null ? IndexerRole.LIVE_WRITER : role;
		return this;
	}

	public IndexResourceOwnership getIndexOwnership() {
		return indexOwnership;
	}

	public AdminCreateTargetIndexerRequest setIndexOwnership(IndexResourceOwnership indexOwnership) {
		this.indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
		return this;
	}

	public IndexerRuntimeState getRuntimeState() {
		return runtimeState;
	}

	public AdminCreateTargetIndexerRequest setRuntimeState(IndexerRuntimeState runtimeState) {
		this.runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
		return this;
	}

	public PublicationState getPublicationState() {
		return publicationState;
	}

	public AdminCreateTargetIndexerRequest setPublicationState(PublicationState publicationState) {
		this.publicationState = publicationState == null ? PublicationState.UNPUBLISHED : publicationState;
		return this;
	}

	public MutationState getMutationState() {
		return mutationState;
	}

	public AdminCreateTargetIndexerRequest setMutationState(MutationState mutationState) {
		this.mutationState = mutationState == null ? MutationState.WRITABLE : mutationState;
		return this;
	}

	public InitialPublicationMode getInitialPublicationMode() {
		return initialPublicationMode;
	}

	public AdminCreateTargetIndexerRequest setInitialPublicationMode(
		InitialPublicationMode initialPublicationMode
	) {
		this.initialPublicationMode = initialPublicationMode;
		return this;
	}
}
