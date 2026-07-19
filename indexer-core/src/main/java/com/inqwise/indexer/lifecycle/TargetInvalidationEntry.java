package com.inqwise.indexer.lifecycle;

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

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer concreteTargetId;
		private Long version;
		private Instant expiresAt;

		private Builder() {
		}

		public Builder withConcreteTargetId(Integer value) {
			concreteTargetId = value;
			return this;
		}

		public Builder withVersion(long value) {
			version = value;
			return this;
		}

		public Builder withExpiresAt(Instant value) {
			expiresAt = value;
			return this;
		}

		public TargetInvalidationEntry build() {
			return new TargetInvalidationEntry(
				concreteTargetId,
				Objects.requireNonNull(version, "version"),
				expiresAt
			);
		}
	}
}
