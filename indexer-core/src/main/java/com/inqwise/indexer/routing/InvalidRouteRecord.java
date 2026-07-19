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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private InvalidRouteSignature signature;
		private String reason;
		private Instant firstSeenAt;
		private Instant lastSeenAt;
		private Instant expiresAt;
		private Long count;

		private Builder() {
		}

		public Builder withSignature(InvalidRouteSignature value) {
			signature = value;
			return this;
		}

		public Builder withReason(String value) {
			reason = value;
			return this;
		}

		public Builder withFirstSeenAt(Instant value) {
			firstSeenAt = value;
			return this;
		}

		public Builder withLastSeenAt(Instant value) {
			lastSeenAt = value;
			return this;
		}

		public Builder withExpiresAt(Instant value) {
			expiresAt = value;
			return this;
		}

		public Builder withCount(long value) {
			count = value;
			return this;
		}

		public InvalidRouteRecord build() {
			return new InvalidRouteRecord(
				Objects.requireNonNull(signature, "signature"),
				Objects.requireNonNull(reason, "reason"),
				Objects.requireNonNull(firstSeenAt, "firstSeenAt"),
				Objects.requireNonNull(lastSeenAt, "lastSeenAt"),
				Objects.requireNonNull(expiresAt, "expiresAt"),
				Objects.requireNonNull(count, "count")
			);
		}
	}
}
