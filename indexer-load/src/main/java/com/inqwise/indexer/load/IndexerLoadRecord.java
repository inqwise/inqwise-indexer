package com.inqwise.indexer.load;

import java.time.Instant;

public record IndexerLoadRecord(
	Integer loadIndexerId,
	Integer liveIndexerId,
	IndexerLoadState state,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	boolean reviewRequired,
	Instant approvedAt,
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
