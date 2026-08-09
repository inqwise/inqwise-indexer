package com.inqwise.indexer.query;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.publication.PublishedIndex;

public record DocumentQueryExecution(
	String reportName,
	String targetName,
	IndexSchema schema,
	List<PublishedIndex> indexes,
	Instant fromInclusive,
	Instant toExclusive,
	QueryFilter filter,
	int limit,
	DocumentQuery query
) {
	public DocumentQueryExecution {
		if (reportName == null || reportName.isBlank()) {
			throw new IllegalArgumentException("reportName must not be blank");
		}
		if (targetName == null || targetName.isBlank()) {
			throw new IllegalArgumentException("targetName must not be blank");
		}
		schema = Objects.requireNonNull(schema, "schema");
		indexes = List.copyOf(Objects.requireNonNull(indexes, "indexes"));
		if (indexes.isEmpty() || indexes.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("indexes must contain non-null values");
		}
		Objects.requireNonNull(fromInclusive, "fromInclusive");
		Objects.requireNonNull(toExclusive, "toExclusive");
		Objects.requireNonNull(filter, "filter");
		Objects.requireNonNull(query, "query");
		if (!fromInclusive.isBefore(toExclusive)) {
			throw new IllegalArgumentException("fromInclusive must be before toExclusive");
		}
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be positive");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String reportName;
		private String targetName;
		private IndexSchema schema;
		private List<PublishedIndex> indexes;
		private Instant fromInclusive;
		private Instant toExclusive;
		private QueryFilter filter;
		private int limit;
		private DocumentQuery query;

		private Builder() {
		}

		public Builder withReportName(String value) {
			reportName = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withSchema(IndexSchema value) {
			schema = value;
			return this;
		}

		public Builder withIndexes(List<PublishedIndex> value) {
			indexes = value == null ? null : List.copyOf(value);
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

		public Builder withFilter(QueryFilter value) {
			filter = value;
			return this;
		}

		public Builder withLimit(int value) {
			limit = value;
			return this;
		}

		public Builder withQuery(DocumentQuery value) {
			query = value;
			return this;
		}

		public DocumentQueryExecution build() {
			return new DocumentQueryExecution(
				Objects.requireNonNull(reportName, "reportName"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(schema, "schema"),
				Objects.requireNonNull(indexes, "indexes"),
				Objects.requireNonNull(fromInclusive, "fromInclusive"),
				Objects.requireNonNull(toExclusive, "toExclusive"),
				Objects.requireNonNull(filter, "filter"),
				limit,
				Objects.requireNonNull(query, "query")
			);
		}
	}
}
