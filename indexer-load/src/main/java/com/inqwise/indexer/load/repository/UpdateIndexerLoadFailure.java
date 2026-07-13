package com.inqwise.indexer.load.repository;

import java.time.Instant;

public record UpdateIndexerLoadFailure(
	Integer indexerId,
	String failureReason,
	Instant failedAt,
	long expectedVersion
) {
}
