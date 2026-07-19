package com.inqwise.indexer.load.events;

import java.time.Instant;
import java.util.Objects;

public record LazyLiveWriterPreparationConflictEvent(
	Integer targetId,
	Integer loadIndexerId,
	Integer candidateLiveIndexerId,
	Integer winnerLiveIndexerId,
	LazyLiveWriterPreparationConflictReason reason,
	boolean cleanupSubmitted,
	Boolean cleanupSucceeded,
	Instant timestamp
) {
	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer targetId;
		private Integer loadIndexerId;
		private Integer candidateLiveIndexerId;
		private Integer winnerLiveIndexerId;
		private LazyLiveWriterPreparationConflictReason reason;
		private Boolean cleanupSubmitted;
		private Boolean cleanupSucceeded;
		private Instant timestamp;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withLoadIndexerId(Integer value) {
			loadIndexerId = value;
			return this;
		}

		public Builder withCandidateLiveIndexerId(Integer value) {
			candidateLiveIndexerId = value;
			return this;
		}

		public Builder withWinnerLiveIndexerId(Integer value) {
			winnerLiveIndexerId = value;
			return this;
		}

		public Builder withReason(LazyLiveWriterPreparationConflictReason value) {
			reason = value;
			return this;
		}

		public Builder withCleanupSubmitted(boolean value) {
			cleanupSubmitted = value;
			return this;
		}

		public Builder withCleanupSucceeded(Boolean value) {
			cleanupSucceeded = value;
			return this;
		}

		public Builder withTimestamp(Instant value) {
			timestamp = value;
			return this;
		}

		public LazyLiveWriterPreparationConflictEvent build() {
			return new LazyLiveWriterPreparationConflictEvent(
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(loadIndexerId, "loadIndexerId"),
				Objects.requireNonNull(candidateLiveIndexerId, "candidateLiveIndexerId"),
				winnerLiveIndexerId,
				Objects.requireNonNull(reason, "reason"),
				Objects.requireNonNull(cleanupSubmitted, "cleanupSubmitted"),
				cleanupSucceeded,
				Objects.requireNonNull(timestamp, "timestamp")
			);
		}
	}
}
