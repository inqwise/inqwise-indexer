package com.inqwise.indexer.metadata;

import java.util.Objects;

public record UpdateIndexerQueueName(
	Integer id,
	String queueName,
	long expectedVersion
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer id;
		private String queueName;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withId(Integer value) {
			id = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public UpdateIndexerQueueName build() {
			return new UpdateIndexerQueueName(
				Objects.requireNonNull(id, "id"),
				Objects.requireNonNull(queueName, "queueName"),
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
