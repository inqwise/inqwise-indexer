package com.inqwise.indexer.example.hn.reports;

import java.util.Objects;

import com.inqwise.indexer.query.DocumentQuery;

public record HackerNewsAuthorSummaryQuery(HackerNewsAuthorOrder orderBy)
	implements DocumentQuery {

	public static final String CAPABILITY = "hacker-news.author-summary";

	public HackerNewsAuthorSummaryQuery {
		Objects.requireNonNull(orderBy, "orderBy");
	}

	@Override
	public String capability() {
		return CAPABILITY;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private HackerNewsAuthorOrder orderBy = HackerNewsAuthorOrder.TOTAL_SCORE;

		private Builder() {
		}

		public Builder withOrderBy(HackerNewsAuthorOrder value) {
			orderBy = value;
			return this;
		}

		public HackerNewsAuthorSummaryQuery build() {
			return new HackerNewsAuthorSummaryQuery(
				Objects.requireNonNull(orderBy, "orderBy")
			);
		}
	}
}
