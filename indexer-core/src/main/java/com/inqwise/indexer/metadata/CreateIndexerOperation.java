package com.inqwise.indexer.metadata;

import java.util.Objects;

import io.vertx.core.Future;

public class CreateIndexerOperation {
	private final DocumentStoreMetadataRepository repository;

	public CreateIndexerOperation(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	public Future<IndexerRecord> create(InsertIndexer insert) {
		return repository.insertIndexer(insert)
			.compose(indexerId -> repository.getIndexerById(indexerId)
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture(
						"Created indexer not found: " + indexerId
					))));
	}
}
