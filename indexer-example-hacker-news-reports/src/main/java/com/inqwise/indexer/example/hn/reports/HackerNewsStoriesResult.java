package com.inqwise.indexer.example.hn.reports;

import java.util.List;
import java.util.Objects;

public record HackerNewsStoriesResult(
	List<HackerNewsStorySummary> stories,
	String nextCursor
) {
	public HackerNewsStoriesResult {
		stories = List.copyOf(Objects.requireNonNull(stories, "stories"));
		if (stories.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("stories must contain non-null values");
		}
		if (nextCursor != null && nextCursor.isBlank()) {
			throw new IllegalArgumentException("nextCursor must not be blank");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<HackerNewsStorySummary> stories = List.of();
		private String nextCursor;

		private Builder() {
		}

		public Builder withStories(List<HackerNewsStorySummary> value) {
			stories = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withNextCursor(String value) {
			nextCursor = value;
			return this;
		}

		public HackerNewsStoriesResult build() {
			return new HackerNewsStoriesResult(
				Objects.requireNonNull(stories, "stories"),
				nextCursor
			);
		}
	}
}
