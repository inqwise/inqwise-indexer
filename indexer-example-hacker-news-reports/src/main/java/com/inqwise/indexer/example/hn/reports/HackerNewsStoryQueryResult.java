package com.inqwise.indexer.example.hn.reports;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.query.DocumentQueryResult;

public record HackerNewsStoryQueryResult(
	List<HackerNewsStorySummary> stories,
	boolean hasMore,
	String requestFingerprint
) implements DocumentQueryResult {
	public HackerNewsStoryQueryResult {
		stories = List.copyOf(Objects.requireNonNull(stories, "stories"));
		if (stories.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("stories must contain non-null values");
		}
		if (requestFingerprint == null || requestFingerprint.isBlank()) {
			throw new IllegalArgumentException("requestFingerprint must not be blank");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<HackerNewsStorySummary> stories = List.of();
		private boolean hasMore;
		private String requestFingerprint;

		private Builder() {
		}

		public Builder withStories(List<HackerNewsStorySummary> value) {
			stories = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withHasMore(boolean value) {
			hasMore = value;
			return this;
		}

		public Builder withRequestFingerprint(String value) {
			requestFingerprint = value;
			return this;
		}

		public HackerNewsStoryQueryResult build() {
			return new HackerNewsStoryQueryResult(
				Objects.requireNonNull(stories, "stories"),
				hasMore,
				Objects.requireNonNull(requestFingerprint, "requestFingerprint")
			);
		}
	}
}
