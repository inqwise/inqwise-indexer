package com.inqwise.indexer.documents;

import java.util.Objects;

import com.inqwise.indexer.provisioning.DocumentIndexNameValidator;

public record DocumentIndexQuery(
	String indexName,
	String queryText,
	int limit
) {
	public DocumentIndexQuery {
		DocumentIndexNameValidator.requireConcrete(indexName);
		queryText = Objects.requireNonNull(queryText, "queryText").trim();
		if (limit < 1) {
			throw new IllegalArgumentException("limit must be positive");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String indexName;
		private String queryText = "";
		private int limit = DocumentQuery.DEFAULT_LIMIT;

		private Builder() {
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withQueryText(String value) {
			queryText = value == null ? "" : value;
			return this;
		}

		public Builder withLimit(int value) {
			limit = value;
			return this;
		}

		public DocumentIndexQuery build() {
			return new DocumentIndexQuery(indexName, queryText, limit);
		}
	}
}
