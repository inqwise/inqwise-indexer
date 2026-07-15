package com.inqwise.indexer.metadata;

import com.inqwise.indexer.publication.ReadinessState;

public record UpdatePublicationReadiness(
	Integer id,
	ReadinessState readinessState,
	String reason,
	long expectedVersion
) {
}
