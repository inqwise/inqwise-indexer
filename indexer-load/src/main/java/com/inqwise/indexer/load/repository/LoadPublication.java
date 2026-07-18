package com.inqwise.indexer.load.repository;

import java.util.Objects;

public record LoadPublication(
	LoadIndexerReference loadWriter,
	LoadIndexerReference candidate,
	LoadIndexerReference oldPublished
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private LoadIndexerReference loadWriter;
		private LoadIndexerReference candidate;
		private LoadIndexerReference oldPublished;

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

		public LoadPublication build() {
			return new LoadPublication(
				Objects.requireNonNull(loadWriter, "loadWriter"),
				Objects.requireNonNull(candidate, "candidate"),
				oldPublished
			);
		}
	}
}
