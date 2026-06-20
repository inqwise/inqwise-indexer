package com.inqwise.indexer.load;

import java.time.Instant;

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
}
