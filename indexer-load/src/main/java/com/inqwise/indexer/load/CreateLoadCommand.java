package com.inqwise.indexer.load;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public class CreateLoadCommand implements Command {
	public static final String TYPE = "indexer.load.create";

	private final String prefix;
	private final String providerId;
	private final String targetName;
	private final String indexName;
	private final String queueName;
	private final LiveWriterPolicy liveWriterPolicy;
	private final String liveQueueName;
	private final Instant reloadStartAt;
	private final Instant liveReplayFrom;
	private final Instant sourceFrom;
	private final Instant sourceTo;
	private final JsonObject sourceQuery;
	private final String sourcePlaybookId;
	private final boolean reviewRequired;

	public CreateLoadCommand(
		String prefix,
		String providerId,
		String targetName,
		String indexName,
		String queueName,
		LiveWriterPolicy liveWriterPolicy,
		String liveQueueName,
		Instant reloadStartAt,
		Instant liveReplayFrom,
		Instant sourceFrom,
		Instant sourceTo,
		JsonObject sourceQuery,
		String sourcePlaybookId,
		boolean reviewRequired
	) {
		this.prefix = prefix;
		this.providerId = Objects.requireNonNull(providerId, "providerId");
		this.targetName = Objects.requireNonNull(targetName, "targetName");
		this.indexName = Objects.requireNonNull(indexName, "indexName");
		this.queueName = Objects.requireNonNull(queueName, "queueName");
		this.liveWriterPolicy = liveWriterPolicy == null ? LiveWriterPolicy.NONE : liveWriterPolicy;
		this.liveQueueName = liveQueueName;
		this.reloadStartAt = reloadStartAt;
		this.liveReplayFrom = liveReplayFrom;
		this.sourceFrom = sourceFrom;
		this.sourceTo = sourceTo;
		this.sourceQuery = sourceQuery == null ? null : sourceQuery.copy();
		this.sourcePlaybookId = sourcePlaybookId;
		this.reviewRequired = reviewRequired;
	}

	public CreateLoadCommand(JsonObject json) {
		this(
			json.getString("prefix"),
			json.getString("provider_id", "default"),
			json.getString("target_name"),
			json.getString("index_name"),
			json.getString("queue_name"),
			LiveWriterPolicy.valueOf(json.getString("live_writer_policy", LiveWriterPolicy.NONE.name())),
			json.getString("live_queue_name"),
			parseInstant(json.getString("reload_start_at")),
			parseInstant(json.getString("live_replay_from")),
			parseInstant(json.getString("source_from")),
			parseInstant(json.getString("source_to")),
			json.getJsonObject("source_query"),
			json.getString("source_playbook_id"),
			json.getBoolean("review_required", false)
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public String getPrefix() {
		return prefix;
	}

	public String getProviderId() {
		return providerId;
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

	public LiveWriterPolicy getLiveWriterPolicy() {
		return liveWriterPolicy;
	}

	public String getLiveQueueName() {
		return liveQueueName;
	}

	public Instant getReloadStartAt() {
		return reloadStartAt;
	}

	public Instant getLiveReplayFrom() {
		return liveReplayFrom;
	}

	public Instant getSourceFrom() {
		return sourceFrom;
	}

	public Instant getSourceTo() {
		return sourceTo;
	}

	public JsonObject getSourceQuery() {
		return sourceQuery == null ? null : sourceQuery.copy();
	}

	public String getSourcePlaybookId() {
		return sourcePlaybookId;
	}

	public boolean isReviewRequired() {
		return reviewRequired;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put("provider_id", providerId)
			.put("target_name", targetName)
			.put("index_name", indexName)
			.put("queue_name", queueName)
			.put("live_writer_policy", liveWriterPolicy.name())
			.put("review_required", reviewRequired);

		putIfPresent(json, "prefix", prefix);
		putIfPresent(json, "live_queue_name", liveQueueName);
		putIfPresent(json, "reload_start_at", reloadStartAt);
		putIfPresent(json, "live_replay_from", liveReplayFrom);
		putIfPresent(json, "source_from", sourceFrom);
		putIfPresent(json, "source_to", sourceTo);
		putIfPresent(json, "source_query", sourceQuery);
		putIfPresent(json, "source_playbook_id", sourcePlaybookId);

		return json;
	}

	private static Instant parseInstant(String value) {
		return value == null ? null : Instant.parse(value);
	}

	private static void putIfPresent(JsonObject json, String name, Object value) {
		if (value instanceof Instant instant) {
			json.put(name, instant.toString());
		} else if (value instanceof JsonObject object) {
			json.put(name, object.copy());
		} else if (value != null) {
			json.put(name, value);
		}
	}
}
