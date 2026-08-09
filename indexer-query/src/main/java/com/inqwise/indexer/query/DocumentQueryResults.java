package com.inqwise.indexer.query;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DocumentQueryResults(
	Instant fromInclusive,
	Instant toExclusive,
	QueryFilter effectiveFilter,
	int effectiveLimit,
	List<DocumentQueryGroupResult> groups
) {
	public DocumentQueryResults {
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");
		Objects.requireNonNull(effectiveFilter, "effectiveFilter");
		groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
		if (effectiveLimit < 1) {
			throw new IllegalArgumentException("effectiveLimit must be positive");
		}
	}

	public boolean emptyScope() {
		return !fromInclusive.isBefore(toExclusive);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Instant fromInclusive;
		private Instant toExclusive;
		private QueryFilter effectiveFilter;
		private int effectiveLimit;
		private List<DocumentQueryGroupResult> groups = List.of();

		private Builder() {
		}

		public Builder withFromInclusive(Instant value) {
			fromInclusive = value;
			return this;
		}

		public Builder withToExclusive(Instant value) {
			toExclusive = value;
			return this;
		}

		public Builder withEffectiveFilter(QueryFilter value) {
			effectiveFilter = value;
			return this;
		}

		public Builder withEffectiveLimit(int value) {
			effectiveLimit = value;
			return this;
		}

		public Builder withGroups(List<DocumentQueryGroupResult> value) {
			groups = value == null ? null : List.copyOf(value);
			return this;
		}

		public DocumentQueryResults build() {
			return new DocumentQueryResults(
				Objects.requireNonNull(fromInclusive, "fromInclusive"),
				Objects.requireNonNull(toExclusive, "toExclusive"),
				Objects.requireNonNull(effectiveFilter, "effectiveFilter"),
				effectiveLimit,
				Objects.requireNonNull(groups, "groups")
			);
		}
	}
}
