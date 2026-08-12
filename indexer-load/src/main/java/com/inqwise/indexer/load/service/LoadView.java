package com.inqwise.indexer.load.service;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.load.api.IndexerLoadRecord;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadView {
	private final JsonObject value;

	public LoadView(JsonObject json) {
		value = Objects.requireNonNull(json, "json").copy();
	}

	public JsonObject toJson() {
		return value.copy();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private IndexerLoadRecord record;

		private Builder() {
		}

		public Builder withRecord(IndexerLoadRecord value) {
			record = value;
			return this;
		}

		public LoadView build() {
			IndexerLoadRecord load = Objects.requireNonNull(record, "record");
			return new LoadView(new JsonObject()
				.put("indexer_id", load.indexerId())
				.put("target_id", load.targetId())
				.put("live_indexer_id", load.liveIndexerId())
				.put("live_writer_policy", name(load.liveWriterPolicy()))
				.put("provider_id", load.providerId())
				.put("state", name(load.state()))
				.put("reload_start_at", string(load.reloadStartAt()))
				.put("live_replay_from", string(load.liveReplayFrom()))
				.put("source_from", string(load.sourceFrom()))
				.put("source_to", string(load.sourceTo()))
				.put("source_playbook_id", load.sourcePlaybookId())
				.put("review_required", load.reviewRequired())
				.put("approved_at", string(load.approvedAt()))
				.put("approved_by", load.approvedBy())
				.put("approval_reason", load.approvalReason())
				.put("last_barrier_id", load.lastBarrierId())
				.put("last_barrier_timestamp", string(load.lastBarrierTimestamp()))
				.put("last_barrier_reached_at", string(load.lastBarrierReachedAt()))
				.put("failure_reason", load.failureReason())
				.put("failed_at", string(load.failedAt()))
				.put("created_at", string(load.createdAt()))
				.put("updated_at", string(load.updatedAt()))
				.put("version", load.version()));
		}
	}

	private static String string(Instant value) {
		return value == null ? null : value.toString();
	}

	private static String name(Enum<?> value) {
		return value == null ? null : value.name();
	}
}
