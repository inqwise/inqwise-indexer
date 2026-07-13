package com.inqwise.indexer.catalog.indexers;

import java.util.Optional;

import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public interface IndexerOperations {
	Future<Optional<IndexerRecord>> markDeleting(MarkIndexerDeletingRequest request);
}
