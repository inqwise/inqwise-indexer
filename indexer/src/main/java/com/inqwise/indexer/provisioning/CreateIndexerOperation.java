package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;

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
