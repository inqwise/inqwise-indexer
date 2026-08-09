package com.inqwise.indexer.query;

import java.util.Objects;

public record DocumentQueryGroupResult(
	IndexSchema schema,
	DocumentQueryResult result
) {
	public DocumentQueryGroupResult {
		schema = Objects.requireNonNull(schema, "schema");
		result = Objects.requireNonNull(result, "result");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private IndexSchema schema;
		private DocumentQueryResult result;

		private Builder() {
		}

		public Builder withSchema(IndexSchema value) {
			schema = value;
			return this;
		}

		public Builder withResult(DocumentQueryResult value) {
			result = value;
			return this;
		}

		public DocumentQueryGroupResult build() {
			return new DocumentQueryGroupResult(
				Objects.requireNonNull(schema, "schema"),
				Objects.requireNonNull(result, "result")
			);
		}
	}
}
