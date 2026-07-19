package com.inqwise.indexer.operations.queues;

import java.util.Objects;

public record ResetIndexerQueueRequest(
	Integer indexerId,
	String expectedQueueName,
	long expectedVersion
) {
	public ResetIndexerQueueRequest {
		Objects.requireNonNull(indexerId, "indexerId");
		Objects.requireNonNull(expectedQueueName, "expectedQueueName");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private String expectedQueueName;
		private Long expectedVersion;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withExpectedQueueName(String value) {
			expectedQueueName = value;
			return this;
		}

		public Builder withExpectedVersion(long value) {
			expectedVersion = value;
			return this;
		}

		public ResetIndexerQueueRequest build() {
			return new ResetIndexerQueueRequest(
				indexerId,
				expectedQueueName,
				Objects.requireNonNull(expectedVersion, "expectedVersion")
			);
		}
	}
}
