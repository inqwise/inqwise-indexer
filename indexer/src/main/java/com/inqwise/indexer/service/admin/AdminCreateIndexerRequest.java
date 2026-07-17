package com.inqwise.indexer.service.admin;

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

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.PREFIX, prefix)
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.INDEX_NAME, indexName)
			.put(Keys.QUEUE_NAME, queueName);
	}

	CreateIndexerProvisioningRequest toProvisioningRequest() {
		return new CreateIndexerProvisioningRequest(
			prefix,
			targetId,
			indexName,
			queueName,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerRuntimeState.NON_ACTIVE
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

}
