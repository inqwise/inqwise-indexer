package com.inqwise.indexer.example.hn.reports;

import java.util.List;
import java.util.Objects;

public record HackerNewsAuthorSummaryResult(List<HackerNewsAuthorSummary> authors) {
	public HackerNewsAuthorSummaryResult {
		authors = List.copyOf(Objects.requireNonNull(authors, "authors"));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<HackerNewsAuthorSummary> authors = List.of();

		private Builder() {
		}

		public Builder withAuthors(List<HackerNewsAuthorSummary> value) {
			authors = value == null ? null : List.copyOf(value);
			return this;
		}

		public HackerNewsAuthorSummaryResult build() {
			return new HackerNewsAuthorSummaryResult(
				Objects.requireNonNull(authors, "authors")
			);
		}
	}
}
