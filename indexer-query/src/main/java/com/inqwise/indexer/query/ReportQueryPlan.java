package com.inqwise.indexer.query;

import java.time.Instant;
import java.util.Objects;

public record ReportQueryPlan(
	Instant fromInclusive,
	Instant toExclusive,
	QueryFilter filter,
	int limit,
	DocumentQuery query
) {
	public ReportQueryPlan {
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");
		Objects.requireNonNull(filter, "filter");
		Objects.requireNonNull(query, "query");
		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be positive");
		}
		if (query.capability() == null || query.capability().isBlank()) {
			throw new IllegalArgumentException("query capability must not be blank");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Instant fromInclusive = Instant.MIN;
		private Instant toExclusive = Instant.MAX;
		private QueryFilter filter = QueryFilters.all();
		private int limit = ReportQueryScope.DEFAULT_MAX_LIMIT;
		private DocumentQuery query;

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

		public Builder withFilter(QueryFilter value) {
			filter = value;
			return this;
		}

		public Builder withLimit(int value) {
			limit = value;
			return this;
		}

		public Builder withQuery(DocumentQuery value) {
			query = value;
			return this;
		}

		public ReportQueryPlan build() {
			return new ReportQueryPlan(
				Objects.requireNonNull(fromInclusive, "fromInclusive"),
				Objects.requireNonNull(toExclusive, "toExclusive"),
				Objects.requireNonNull(filter, "filter"),
				limit,
				Objects.requireNonNull(query, "query")
			);
		}
	}
}
