package com.inqwise.indexer.load;

import java.time.Instant;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record CreateLoadRequest(
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
	public CreateLoadRequest {
		Objects.requireNonNull(providerId, "providerId");
		Objects.requireNonNull(targetName, "targetName");
		Objects.requireNonNull(indexName, "indexName");
		Objects.requireNonNull(queueName, "queueName");
		liveWriterPolicy = liveWriterPolicy == null ? LiveWriterPolicy.NONE : liveWriterPolicy;
		sourceQuery = sourceQuery == null ? null : sourceQuery.copy();
	}

	@Override
	public JsonObject sourceQuery() {
		return sourceQuery == null ? null : sourceQuery.copy();
	}
}
