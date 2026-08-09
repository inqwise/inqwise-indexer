package com.inqwise.indexer.query;

import java.time.Instant;
import java.util.Objects;

public record ReportQueryScope(
	Instant fromInclusive,
	Instant toExclusive,
	QueryFilter mandatoryFilter,
	int maxLimit
) {
	public static final int DEFAULT_MAX_LIMIT = 100;

	public ReportQueryScope {
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");
		Objects.requireNonNull(mandatoryFilter, "mandatoryFilter");
		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
		if (maxLimit < 1) {
			throw new IllegalArgumentException("maxLimit must be positive");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Instant fromInclusive = Instant.MIN;
		private Instant toExclusive = Instant.MAX;
		private QueryFilter mandatoryFilter = QueryFilters.all();
		private int maxLimit = DEFAULT_MAX_LIMIT;

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

		public Builder withMandatoryFilter(QueryFilter value) {
			mandatoryFilter = value;
			return this;
		}

		public Builder withMaxLimit(int value) {
			maxLimit = value;
			return this;
		}

		public ReportQueryScope build() {
			return new ReportQueryScope(
				Objects.requireNonNull(fromInclusive, "fromInclusive"),
				Objects.requireNonNull(toExclusive, "toExclusive"),
				Objects.requireNonNull(mandatoryFilter, "mandatoryFilter"),
				maxLimit
			);
		}
	}
}
