package com.inqwise.indexer.management.queues;

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
}
