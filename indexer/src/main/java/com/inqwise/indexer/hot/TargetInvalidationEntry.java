package com.inqwise.indexer.hot;

import java.time.Instant;
import java.util.Objects;

public record TargetInvalidationEntry(
	Integer concreteTargetId,
	long version,
	Instant expiresAt
) {
	public TargetInvalidationEntry {
		Objects.requireNonNull(concreteTargetId, "concreteTargetId");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}
}
