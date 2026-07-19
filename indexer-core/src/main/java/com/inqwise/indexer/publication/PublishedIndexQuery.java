package com.inqwise.indexer.publication;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetNameValidator;

public record PublishedIndexQuery(
	String targetName,
	Instant fromInclusive,
	Instant toExclusive
) {
	public PublishedIndexQuery {
		TargetNameValidator.requireTargetName(targetName);
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");

		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private Instant fromInclusive;
		private Instant toExclusive;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withFromInclusive(Instant value) {
			fromInclusive = value;
			return this;
		}

		public Builder withToExclusive(Instant value) {
			toExclusive = value;
			return this;
		}

		public PublishedIndexQuery build() {
			return new PublishedIndexQuery(
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(fromInclusive, "fromInclusive"),
				Objects.requireNonNull(toExclusive, "toExclusive")
			);
		}
	}
}
