package com.inqwise.indexer.load.api;

import java.time.Instant;
import java.util.Objects;

public record ApproveLoadPublicationRequest(
	Integer indexerId,
	Instant approvedAt,
	String approvedBy,
	String approvalReason,
	long expectedVersion
) {
	public ApproveLoadPublicationRequest {
		Objects.requireNonNull(indexerId, "indexerId");
		if (expectedVersion < 0) {
			throw new IllegalArgumentException("expectedVersion must not be negative");
		}
	}
}
