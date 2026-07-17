package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

public record CreateTargetIndexerRequest(
	String prefix,
	String indexName,
	String queueName,
	InitialPublicationMode initialPublicationMode
) {
	public CreateTargetIndexerRequest {
		Objects.requireNonNull(indexName, "indexName");
		Objects.requireNonNull(queueName, "queueName");
		Objects.requireNonNull(initialPublicationMode, "initialPublicationMode");
	}
}
