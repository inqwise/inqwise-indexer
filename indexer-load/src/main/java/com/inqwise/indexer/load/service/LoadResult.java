package com.inqwise.indexer.load.service;

import com.inqwise.indexer.load.api.IndexerLoadRecord;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadResult {
	private JsonObject load;

	public LoadResult() {
	}

	public LoadResult(JsonObject json) {
		this.load = copy(json.getJsonObject("load"));
	}

	public JsonObject toJson() {
		return new JsonObject().put("load", copy(load));
	}

	public static LoadResult from(IndexerLoadRecord record) {
		return new LoadResult().setLoad(new JsonObject()
			.put("indexer_id", record.indexerId())
			.put("target_id", record.targetId())
			.put("live_indexer_id", record.liveIndexerId())
			.put("live_writer_policy", name(record.liveWriterPolicy()))
			.put("provider_id", record.providerId())
			.put("state", name(record.state()))
			.put("reload_start_at", string(record.reloadStartAt()))
			.put("live_replay_from", string(record.liveReplayFrom()))
			.put("source_from", string(record.sourceFrom()))
			.put("source_to", string(record.sourceTo()))
			.put("source_query", copy(record.sourceQuery()))
			.put("source_playbook_id", record.sourcePlaybookId())
			.put("review_required", record.reviewRequired())
			.put("approved_at", string(record.approvedAt()))
			.put("approved_by", record.approvedBy())
			.put("approval_reason", record.approvalReason())
			.put("last_barrier_id", record.lastBarrierId())
			.put("last_barrier_timestamp", string(record.lastBarrierTimestamp()))
			.put("last_barrier_reached_at", string(record.lastBarrierReachedAt()))
			.put("failure_reason", record.failureReason())
			.put("failed_at", string(record.failedAt()))
			.put("created_at", string(record.createdAt()))
			.put("updated_at", string(record.updatedAt()))
			.put("version", record.version()));
	}

	public JsonObject getLoad() {
		return copy(load);
	}

	public LoadResult setLoad(JsonObject load) {
		this.load = copy(load);
		return this;
	}

	private static String string(java.time.Instant value) {
		return value == null ? null : value.toString();
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}

	private static JsonObject copy(JsonObject value) {
		return value == null ? null : value.copy();
	}
}
