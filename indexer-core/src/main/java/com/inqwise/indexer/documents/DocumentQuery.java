package com.inqwise.indexer.documents;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.catalog.targets.TargetNameValidator;

public record DocumentQuery(
	String targetName,
	String queryText,
	Instant fromInclusive,
	Instant toExclusive,
	int offset,
	int limit
) {
	public static final int DEFAULT_LIMIT = 20;
	public static final int MAX_LIMIT = 100;
	public static final int MAX_OFFSET = 10_000;
	public static final int MAX_QUERY_TEXT_LENGTH = 1_000;

	public DocumentQuery {
		TargetNameValidator.requireTargetName(targetName);
		queryText = Objects.requireNonNull(queryText, "queryText").trim();
		fromInclusive = Objects.requireNonNull(fromInclusive, "fromInclusive");
		toExclusive = Objects.requireNonNull(toExclusive, "toExclusive");
		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
		if (queryText.length() > MAX_QUERY_TEXT_LENGTH) {
			throw new IllegalArgumentException(
				"queryText must not exceed " + MAX_QUERY_TEXT_LENGTH + " characters"
			);
		}
		if (offset < 0 || offset > MAX_OFFSET) {
			throw new IllegalArgumentException(
				"offset must be between 0 and " + MAX_OFFSET
			);
		}
		if (limit < 1 || limit > MAX_LIMIT) {
			throw new IllegalArgumentException(
				"limit must be between 1 and " + MAX_LIMIT
			);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private String queryText = "";
		private Instant fromInclusive = Instant.MIN;
		private Instant toExclusive = Instant.MAX;
		private int offset;
		private int limit = DEFAULT_LIMIT;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withQueryText(String value) {
			queryText = value == null ? "" : value;
			return this;
		}

		public Builder withFromInclusive(Instant value) {
			fromInclusive = value;
			return this;
		}

		public Builder withToExclusive(Instant value) {
			toExclusive = value;
			return this;
		}

		public Builder withOffset(int value) {
			offset = value;
			return this;
		}

		public Builder withLimit(int value) {
			limit = value;
			return this;
		}

		public DocumentQuery build() {
			return new DocumentQuery(
				targetName,
				queryText,
				fromInclusive,
				toExclusive,
				offset,
				limit
			);
		}
	}
}
