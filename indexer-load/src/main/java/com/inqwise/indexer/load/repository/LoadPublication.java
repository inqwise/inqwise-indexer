package com.inqwise.indexer.load.repository;

import java.util.Objects;

public record LoadPublication(
	LoadIndexerReference loadWriter,
	LoadIndexerReference candidate,
	LoadIndexerReference oldPublished,
	long expectedLoadVersion
) {
	public LoadPublication {
		Objects.requireNonNull(loadWriter, "loadWriter");
		Objects.requireNonNull(candidate, "candidate");
		if (expectedLoadVersion < 0 || expectedLoadVersion == Long.MAX_VALUE) {
			throw new IllegalArgumentException("expectedLoadVersion must allow a publication transition");
		}
		if (!loadWriter.targetId().equals(candidate.targetId())
			|| (oldPublished != null && (!loadWriter.targetId().equals(oldPublished.targetId())
				|| oldPublished.id().equals(candidate.id()) || oldPublished.id().equals(loadWriter.id())))) {
			throw new IllegalArgumentException("Publication references must identify distinct writers on one target");
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private LoadIndexerReference loadWriter;
		private LoadIndexerReference candidate;
		private LoadIndexerReference oldPublished;
		private Long expectedLoadVersion;

		private Builder() {
		}

		public Builder withLoadWriter(LoadIndexerReference value) {
			loadWriter = value;
			return this;
		}

		public Builder withCandidate(LoadIndexerReference value) {
			candidate = value;
			return this;
		}

		public Builder withOldPublished(LoadIndexerReference value) {
			oldPublished = value;
			return this;
		}

		public Builder withExpectedLoadVersion(long value) {
			expectedLoadVersion = value;
			return this;
		}

		public LoadPublication build() {
			return new LoadPublication(
				Objects.requireNonNull(loadWriter, "loadWriter"),
				Objects.requireNonNull(candidate, "candidate"),
				oldPublished,
				Objects.requireNonNull(expectedLoadVersion, "expectedLoadVersion")
			);
		}
	}
}
