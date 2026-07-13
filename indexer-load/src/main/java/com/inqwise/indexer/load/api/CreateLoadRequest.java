package com.inqwise.indexer.load.api;

import java.time.Instant;
import java.util.Objects;

import io.vertx.core.json.JsonObject;

public record CreateLoadRequest(
	String providerId,
	Integer targetId,
	LiveWriterPolicy liveWriterPolicy,
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
		Objects.requireNonNull(targetId, "targetId");
		liveWriterPolicy = liveWriterPolicy == null ? LiveWriterPolicy.NONE : liveWriterPolicy;
		sourceQuery = sourceQuery == null ? null : sourceQuery.copy();
	}

	@Override
	public JsonObject sourceQuery() {
		return sourceQuery == null ? null : sourceQuery.copy();
	}
}
