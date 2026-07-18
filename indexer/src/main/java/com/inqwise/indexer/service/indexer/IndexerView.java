package com.inqwise.indexer.service.indexer;

import java.time.Instant;

import com.inqwise.indexer.catalog.indexers.IndexerCatalogEntry;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerView {
	private JsonObject value;

	public IndexerView() {
		value = new JsonObject();
	}

	public IndexerView(JsonObject json) {
		value = json.copy();
	}

	public JsonObject toJson() {
		return value.copy();
	}

	public static IndexerView from(IndexerCatalogEntry entry) {
		return new IndexerView(new JsonObject()
			.put("id", entry.id())
			.put("uid", entry.uid())
			.put("target_id", entry.targetId())
			.put("target_name", entry.targetName())
			.put("index_name", entry.indexName())
			.put("queue_name", entry.queueName())
			.put("type", entry.type().name())
			.put("role", entry.role().name())
			.put("index_ownership", entry.indexOwnership().name())
			.put("status", entry.status().name())
			.put("provisioning_state", entry.provisioningState().name())
			.put("runtime_state", entry.runtimeState().name())
			.put("mutation_state", entry.mutationState().name())
			.put("created_at", string(entry.createdAt()))
			.put("updated_at", string(entry.updatedAt()))
			.put("version", entry.version()));
	}

	public Integer getId() {
		return value.getInteger("id");
	}

	public String getUid() {
		return value.getString("uid");
	}

	public Integer getTargetId() {
		return value.getInteger("target_id");
	}

	public String getIndexName() {
		return value.getString("index_name");
	}

	public String getQueueName() {
		return value.getString("queue_name");
	}

	public IndexerRuntimeState getRuntimeState() {
		return IndexerRuntimeState.valueOf(value.getString("runtime_state"));
	}

	public long getVersion() {
		return value.getLong("version", 0L);
	}

	private static String string(Instant value) {
		return value == null ? null : value.toString();
	}
}
