package com.inqwise.indexer.example.hn.reports;

import java.time.Instant;
import java.util.Objects;

public record HackerNewsStorySummary(
	long id,
	String author,
	String title,
	String url,
	Instant time,
	int score,
	int descendants
) {
	public HackerNewsStorySummary {
		if (id < 1) {
			throw new IllegalArgumentException("id must be positive");
		}
		if (title == null || title.isBlank()) {
			throw new IllegalArgumentException("title must not be blank");
		}
		Objects.requireNonNull(time, "time");
		if (score < 0) {
			throw new IllegalArgumentException("score must not be negative");
		}
		if (descendants < 0) {
			throw new IllegalArgumentException("descendants must not be negative");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Long id;
		private String author;
		private String title;
		private String url;
		private Instant time;
		private int score;
		private int descendants;

		private Builder() {
		}

		public Builder withId(long value) {
			id = value;
			return this;
		}

		public Builder withAuthor(String value) {
			author = value;
			return this;
		}

		public Builder withTitle(String value) {
			title = value;
			return this;
		}

		public Builder withUrl(String value) {
			url = value;
			return this;
		}

		public Builder withTime(Instant value) {
			time = value;
			return this;
		}

		public Builder withScore(int value) {
			score = value;
			return this;
		}

		public Builder withDescendants(int value) {
			descendants = value;
			return this;
		}

		public HackerNewsStorySummary build() {
			return new HackerNewsStorySummary(
				Objects.requireNonNull(id, "id"),
				author,
				Objects.requireNonNull(title, "title"),
				url,
				Objects.requireNonNull(time, "time"),
				score,
				descendants
			);
		}
	}
}
