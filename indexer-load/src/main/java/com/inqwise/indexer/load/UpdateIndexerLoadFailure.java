package com.inqwise.indexer.load;

import java.time.Instant;

public record UpdateIndexerLoadFailure(
	Integer loadIndexerId,
	String failureReason,
	Instant failedAt,
	long expectedVersion
) {
}
