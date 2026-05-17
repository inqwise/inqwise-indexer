package com.inqwise.indexer.metadata;

public record UpdatePublicationReadiness(
	Integer id,
	ReadinessState readinessState,
	String reason,
	long expectedVersion
) {
}
