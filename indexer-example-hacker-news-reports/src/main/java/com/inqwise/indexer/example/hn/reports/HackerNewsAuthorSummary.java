package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

public record HackerNewsAuthorSummary(
	String author,
	long storyCount,
	long totalScore,
	int maxScore,
	Instant latestStoryTime
) {
	public HackerNewsAuthorSummary {
		if (author == null || author.isBlank()) {
			throw new IllegalArgumentException("author must not be blank");
		}
		if (storyCount < 1) {
			throw new IllegalArgumentException("storyCount must be positive");
		}
		if (totalScore < 0) {
			throw new IllegalArgumentException("totalScore must not be negative");
		}
		if (maxScore < 0) {
			throw new IllegalArgumentException("maxScore must not be negative");
		}
		Objects.requireNonNull(latestStoryTime, "latestStoryTime");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Comparator<HackerNewsAuthorSummary> comparator(
		HackerNewsAuthorOrder order
	) {
		Comparator<HackerNewsAuthorSummary> comparator = switch (order) {
			case TOTAL_SCORE -> Comparator.comparingLong(
				HackerNewsAuthorSummary::totalScore
			).reversed();
			case STORY_COUNT -> Comparator.comparingLong(
				HackerNewsAuthorSummary::storyCount
			).reversed();
			case MAX_SCORE -> Comparator.comparingInt(
				HackerNewsAuthorSummary::maxScore
			).reversed();
			case LATEST_STORY -> Comparator.comparing(
				HackerNewsAuthorSummary::latestStoryTime,
				Comparator.reverseOrder()
			);
		};
		return comparator.thenComparing(HackerNewsAuthorSummary::author);
	}

	public static final class Builder {
		private String author;
		private long storyCount;
		private long totalScore;
		private int maxScore;
		private Instant latestStoryTime;

		private Builder() {
		}

		public Builder withAuthor(String value) {
			author = value;
			return this;
		}

		public Builder withStoryCount(long value) {
			storyCount = value;
			return this;
		}

		public Builder withTotalScore(long value) {
			totalScore = value;
			return this;
		}

		public Builder withMaxScore(int value) {
			maxScore = value;
			return this;
		}

		public Builder withLatestStoryTime(Instant value) {
			latestStoryTime = value;
			return this;
		}

		public HackerNewsAuthorSummary build() {
			return new HackerNewsAuthorSummary(
				Objects.requireNonNull(author, "author"),
				storyCount,
				totalScore,
				maxScore,
				Objects.requireNonNull(latestStoryTime, "latestStoryTime")
			);
		}
	}
}
