package com.inqwise.indexer.routing;

import java.time.Instant;
import java.util.Objects;

public record InvalidRouteRecord(
	InvalidRouteSignature signature,
	String reason,
	Instant firstSeenAt,
	Instant lastSeenAt,
	Instant expiresAt,
	long count
) {
	public InvalidRouteRecord {
		Objects.requireNonNull(signature, "signature");
		Objects.requireNonNull(reason, "reason");
		Objects.requireNonNull(firstSeenAt, "firstSeenAt");
		Objects.requireNonNull(lastSeenAt, "lastSeenAt");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}
}
