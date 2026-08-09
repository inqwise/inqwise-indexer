package com.inqwise.indexer.example.hn.reports;

import java.util.Objects;

import com.inqwise.indexer.query.DocumentQuery;

public record HackerNewsStoryQuery(
	HackerNewsStoriesCursor cursor,
	String requestFingerprint
) implements DocumentQuery {
	public static final String CAPABILITY = "hacker-news.story-search";

	public HackerNewsStoryQuery {
		if (requestFingerprint == null || requestFingerprint.isBlank()) {
			throw new IllegalArgumentException("requestFingerprint must not be blank");
		}
		if (cursor != null
			&& !requestFingerprint.equals(cursor.requestFingerprint())) {
			throw new IllegalArgumentException(
				"cursor and query request fingerprints must match"
			);
		}
	}

	@Override
	public String capability() {
		return CAPABILITY;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private HackerNewsStoriesCursor cursor;
		private String requestFingerprint;

		private Builder() {
		}

		public Builder withCursor(HackerNewsStoriesCursor value) {
			cursor = value;
			return this;
		}

		public Builder withRequestFingerprint(String value) {
			requestFingerprint = value;
			return this;
		}

		public HackerNewsStoryQuery build() {
			return new HackerNewsStoryQuery(
				cursor,
				Objects.requireNonNull(requestFingerprint, "requestFingerprint")
			);
		}
	}
}
