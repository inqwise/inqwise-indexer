package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.publication.ReadinessState;

public record InsertPublication(
	String prefix,
	Integer indexerId,
	Integer targetId,
	String targetName,
	String indexName,
	ReadinessState readinessState,
	String reason
) {
	public InsertPublication {
		Objects.requireNonNull(prefix, "prefix");
	}
}
