package com.inqwise.indexer.metadata;

public record UpdateIndexerQueueName(
	Integer id,
	String queueName,
	long expectedVersion
) {
}
