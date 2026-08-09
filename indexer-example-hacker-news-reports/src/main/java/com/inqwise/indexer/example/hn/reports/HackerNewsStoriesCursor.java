package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.util.Objects;

public record HackerNewsStoriesCursor(
	int score,
	Instant time,
	long id,
	String requestFingerprint
) {
	public HackerNewsStoriesCursor {
		Objects.requireNonNull(time, "time");
		if (score < 0) {
			throw new IllegalArgumentException("cursor score must not be negative");
		}
		if (id < 1) {
			throw new IllegalArgumentException("cursor id must be positive");
		}
		if (requestFingerprint == null || requestFingerprint.isBlank()) {
			throw new IllegalArgumentException("cursor requestFingerprint must not be blank");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private int score;
		private Instant time;
		private long id;
		private String requestFingerprint;

		private Builder() {
		}

		public Builder withScore(int value) {
			score = value;
			return this;
		}

		public Builder withTime(Instant value) {
			time = value;
			return this;
		}

		public Builder withId(long value) {
			id = value;
			return this;
		}

		public Builder withRequestFingerprint(String value) {
			requestFingerprint = value;
			return this;
		}

		public HackerNewsStoriesCursor build() {
			return new HackerNewsStoriesCursor(
				score,
				Objects.requireNonNull(time, "time"),
				id,
				Objects.requireNonNull(requestFingerprint, "requestFingerprint")
			);
		}
	}
}
