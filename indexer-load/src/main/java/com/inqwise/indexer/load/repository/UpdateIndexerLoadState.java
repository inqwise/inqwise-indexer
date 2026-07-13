package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadState;


public record UpdateIndexerLoadState(
	Integer indexerId,
	IndexerLoadState state,
	long expectedVersion
) {
}
