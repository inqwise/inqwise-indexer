package com.inqwise.indexer.catalog.indexers;

import java.util.Optional;

import io.vertx.core.Future;

public interface IndexerOperations {
	Future<Optional<IndexerDeletionResult>> markDeleting(MarkIndexerDeletingRequest request);
}
