package com.inqwise.indexer.service.admin;

import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.targets.CreateTargetIndexerRequest;
import com.inqwise.indexer.catalog.targets.InitialPublicationMode;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminCreateTargetIndexerRequest {
	public static final class Keys {
		public static final String PREFIX = "prefix";
		public static final String INDEX_NAME = "index_name";
		public static final String QUEUE_NAME = "queue_name";
		public static final String ROLE = "role";
		public static final String INDEX_OWNERSHIP = "index_ownership";
		public static final String RUNTIME_STATE = "runtime_state";
		public static final String INITIAL_PUBLICATION_MODE = "initial_publication_mode";

		private Keys() {
		}
	}

	private String prefix;
	private String indexName;
	private String queueName;
	private IndexerRole role = IndexerRole.LIVE_WRITER;
	private IndexResourceOwnership indexOwnership = IndexResourceOwnership.OWNER;
	private IndexerRuntimeState runtimeState = IndexerRuntimeState.NON_ACTIVE;
	private InitialPublicationMode initialPublicationMode;

	public AdminCreateTargetIndexerRequest() {
	}

	public AdminCreateTargetIndexerRequest(JsonObject json) {
		this.prefix = json.getString(Keys.PREFIX);
		this.indexName = json.getString(Keys.INDEX_NAME);
		this.queueName = json.getString(Keys.QUEUE_NAME);
		this.role = IndexerRole.valueOf(json.getString(Keys.ROLE, IndexerRole.LIVE_WRITER.name()));
		this.indexOwnership = IndexResourceOwnership.valueOf(json.getString(
			Keys.INDEX_OWNERSHIP,
			IndexResourceOwnership.OWNER.name()
		));
		this.runtimeState = IndexerRuntimeState.valueOf(json.getString(
			Keys.RUNTIME_STATE,
			IndexerRuntimeState.NON_ACTIVE.name()
		));
		String mode = json.getString(Keys.INITIAL_PUBLICATION_MODE);
		this.initialPublicationMode = mode == null ? null : InitialPublicationMode.valueOf(mode);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.PREFIX, prefix)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName)
			.put(Keys.ROLE, role.name())
			.put(Keys.INDEX_OWNERSHIP, indexOwnership.name())
			.put(Keys.RUNTIME_STATE, runtimeState.name())
			.put(Keys.INITIAL_PUBLICATION_MODE, initialPublicationMode == null
				? null
				: initialPublicationMode.name());
	}

	CreateTargetIndexerRequest toTargetRequest() {
		return new CreateTargetIndexerRequest(
			prefix,
			indexName,
			queueName,
			role,
			indexOwnership,
			runtimeState,
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
