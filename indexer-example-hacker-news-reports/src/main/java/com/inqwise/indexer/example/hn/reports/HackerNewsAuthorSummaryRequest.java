package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.util.Objects;

public record HackerNewsAuthorSummaryRequest(
	Instant fromInclusive,
	Instant toExclusive,
	int minimumScore,
	int limit,
	HackerNewsAuthorOrder orderBy
) {
	public HackerNewsAuthorSummaryRequest {
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");
		Objects.requireNonNull(orderBy, "orderBy");
		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
		if (minimumScore < 0) {
			throw new IllegalArgumentException("minimumScore must not be negative");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be positive");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Instant fromInclusive;
		private Instant toExclusive;
		private int minimumScore;
		private int limit = 25;
		private HackerNewsAuthorOrder orderBy = HackerNewsAuthorOrder.TOTAL_SCORE;

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

		public Builder withMinimumScore(int value) {
			minimumScore = value;
			return this;
		}

		public Builder withLimit(int value) {
			limit = value;
			return this;
		}

		public Builder withOrderBy(HackerNewsAuthorOrder value) {
			orderBy = value;
			return this;
		}

		public HackerNewsAuthorSummaryRequest build() {
			return new HackerNewsAuthorSummaryRequest(
				Objects.requireNonNull(fromInclusive, "fromInclusive"),
				Objects.requireNonNull(toExclusive, "toExclusive"),
				minimumScore,
				limit,
				Objects.requireNonNull(orderBy, "orderBy")
			);
		}
	}
}
