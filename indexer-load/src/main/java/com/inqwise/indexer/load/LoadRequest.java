package com.inqwise.indexer.load;

import java.time.Instant;

public record LoadRequest(
	Integer loadIndexerId,
	Integer targetId,
	String indexName,
	String queueName,
	Instant reloadStartAt,
	Instant liveReplayFrom
) {
}
