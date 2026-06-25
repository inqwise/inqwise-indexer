package com.inqwise.indexer.operations;

import java.util.Optional;

import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public interface IndexerOperations {
	Future<Optional<IndexerRecord>> markDeleting(MarkIndexerDeletingRequest request);
}
