package com.inqwise.indexer.publication;

import java.util.Objects;

public record PublishedIndex(
	Integer indexerId,
	Integer targetId,
	String indexName,
	String schemaName,
	String schemaVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private String indexName;
		private String schemaName;
		private String schemaVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withSchemaName(String value) {
			schemaName = value;
			return this;
		}

		public Builder withSchemaVersion(String value) {
			schemaVersion = value;
			return this;
		}

		public PublishedIndex build() {
			return new PublishedIndex(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(indexName, "indexName"),
				Objects.requireNonNull(schemaName, "schemaName"),
				Objects.requireNonNull(schemaVersion, "schemaVersion")
			);
		}
	}
}
