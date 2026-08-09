package com.inqwise.indexer.example.hn.reports;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.query.DocumentQueryResult;

public record HackerNewsAuthorSummaryQueryResult(
	List<HackerNewsAuthorSummary> authors,
	HackerNewsAuthorOrder orderBy
) implements DocumentQueryResult {
	public HackerNewsAuthorSummaryQueryResult {
		authors = List.copyOf(Objects.requireNonNull(authors, "authors"));
		Objects.requireNonNull(orderBy, "orderBy");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<HackerNewsAuthorSummary> authors = List.of();
		private HackerNewsAuthorOrder orderBy;

		private Builder() {
		}

		public Builder withAuthors(List<HackerNewsAuthorSummary> value) {
			authors = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withOrderBy(HackerNewsAuthorOrder value) {
			orderBy = value;
			return this;
		}

		public HackerNewsAuthorSummaryQueryResult build() {
			return new HackerNewsAuthorSummaryQueryResult(
				Objects.requireNonNull(authors, "authors"),
				Objects.requireNonNull(orderBy, "orderBy")
			);
		}
	}
}
