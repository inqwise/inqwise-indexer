package com.inqwise.indexer.example.hn.reports;

import com.inqwise.indexer.query.QueryFilter;

public record HackerNewsStoryFilter(int minimumScore) implements QueryFilter {
	public HackerNewsStoryFilter {
		if (minimumScore < 0) {
			throw new IllegalArgumentException("minimumScore must not be negative");
		}
	}

	public String itemType() {
		return "story";
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private int minimumScore;

		private Builder() {
		}

		public Builder withMinimumScore(int value) {
			minimumScore = value;
			return this;
		}

		public HackerNewsStoryFilter build() {
			return new HackerNewsStoryFilter(minimumScore);
		}
	}
}
