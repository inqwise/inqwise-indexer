package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.util.Objects;

public record HackerNewsStoriesRequest(
	Instant fromInclusive,
	Instant toExclusive,
	int minimumScore,
	int limit,
	String cursor
) {
	public HackerNewsStoriesRequest {
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");
		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
		if (minimumScore < 0) {
			throw new IllegalArgumentException("minimumScore must not be negative");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be positive");
		}
		cursor = cursor == null || cursor.isBlank() ? null : cursor;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Instant fromInclusive;
		private Instant toExclusive;
		private int minimumScore;
		private int limit = 25;
		private String cursor;

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

		public Builder withCursor(String value) {
			cursor = value;
			return this;
		}

		public HackerNewsStoriesRequest build() {
			return new HackerNewsStoriesRequest(
				Objects.requireNonNull(fromInclusive, "fromInclusive"),
				Objects.requireNonNull(toExclusive, "toExclusive"),
				minimumScore,
				limit,
				cursor
			);
		}
	}
}
