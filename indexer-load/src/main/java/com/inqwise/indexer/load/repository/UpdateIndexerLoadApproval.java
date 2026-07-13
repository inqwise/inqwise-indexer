package com.inqwise.indexer.load.repository;

import java.time.Instant;

public record UpdateIndexerLoadApproval(
	Integer indexerId,
	Instant approvedAt,
	String approvedBy,
	String approvalReason,
	long expectedVersion
) {
}
