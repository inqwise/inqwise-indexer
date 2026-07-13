package com.inqwise.indexer.load.events;

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
