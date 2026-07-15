package com.inqwise.indexer.provisioning.definitions;

import java.util.Objects;

public record IndexerDefinition(
	IndexDefinition index,
	QueueDefinition queue
) {
	public IndexerDefinition {
		index = Objects.requireNonNull(index, "index");
		queue = Objects.requireNonNull(queue, "queue");
	}
}
