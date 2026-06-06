package com.inqwise.indexer.load;

import java.time.Instant;

import io.vertx.core.json.JsonObject;

public record IndexerLoadRecord(
	Integer indexerId,
	Integer targetId,
	Integer liveIndexerId,
	LiveWriterPolicy liveWriterPolicy,
	String providerId,
	IndexerLoadState state,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	Instant sourceFrom,
	Instant sourceTo,
	JsonObject sourceQuery,
	String sourcePlaybookId,
	boolean reviewRequired,
	Instant approvedAt,
	String approvedBy,
	String approvalReason,
	String lastBarrierId,
	Instant lastBarrierTimestamp,
	Instant lastBarrierReachedAt,
	String failureReason,
	Instant failedAt,
	Instant createdAt,
	Instant updatedAt,
	long version
) {
}
