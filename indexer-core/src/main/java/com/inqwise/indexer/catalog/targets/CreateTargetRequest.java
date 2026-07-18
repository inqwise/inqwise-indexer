package com.inqwise.indexer.catalog.targets;

import java.time.Instant;

public record CreateTargetRequest(
	String targetName,
	Instant timestamp,
	CreateTargetIndexerRequest createIndexer
) {
	public CreateTargetRequest {
		TargetNameValidator.requireTargetName(targetName);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private Instant timestamp;
		private CreateTargetIndexerRequest createIndexer;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withTimestamp(Instant value) {
			timestamp = value;
			return this;
		}

		public Builder withCreateIndexer(CreateTargetIndexerRequest value) {
			createIndexer = value;
			return this;
		}

		public CreateTargetRequest build() {
			return new CreateTargetRequest(targetName, timestamp, createIndexer);
		}
	}
}
