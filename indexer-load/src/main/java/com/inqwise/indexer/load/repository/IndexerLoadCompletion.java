package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadState;

import java.time.Instant;
import java.util.Objects;

public record IndexerLoadCompletion(
	Integer indexerId,
	IndexerLoadState terminalState,
	long terminalVersion,
	Instant completedAt
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private IndexerLoadState terminalState;
		private long terminalVersion;
		private Instant completedAt;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTerminalState(IndexerLoadState value) {
			terminalState = value;
			return this;
		}

		public Builder withTerminalVersion(long value) {
			terminalVersion = value;
			return this;
		}

		public Builder withCompletedAt(Instant value) {
			completedAt = value;
			return this;
		}

		public IndexerLoadCompletion build() {
			return new IndexerLoadCompletion(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(terminalState, "terminalState"),
				terminalVersion,
				Objects.requireNonNull(completedAt, "completedAt")
			);
		}
	}
}
