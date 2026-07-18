package com.inqwise.indexer.catalog.indexers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerMetadataQuery;
import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public final class MetadataIndexerCatalogReader implements IndexerCatalogReader {
	private final DocumentStoreMetadataRepository repository;

	public MetadataIndexerCatalogReader(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public Future<List<IndexerCatalogEntry>> list(IndexerCatalogQuery query) {
		IndexerCatalogQuery resolved = query == null
			? new IndexerCatalogQuery(null, null, null, null, null, null, null, null)
			: query;
		return repository.listIndexers(new IndexerMetadataQuery(
			resolved.ids(),
			resolved.targetIds(),
			resolved.types(),
			resolved.roles(),
			resolved.statuses(),
			resolved.provisioningStates(),
			resolved.runtimeStates(),
			List.of(),
			resolved.mutationStates()
		)).map(records -> records.stream()
			.map(MetadataIndexerCatalogReader::toEntry)
			.toList());
	}

	@Override
	public Future<Optional<IndexerCatalogEntry>> findById(Integer id) {
		return repository.getIndexerById(id)
			.map(found -> found.map(MetadataIndexerCatalogReader::toEntry));
	}

	@Override
	public Future<Optional<IndexerCatalogEntry>> findByUid(String uid) {
		return repository.getIndexerByUid(uid)
			.map(found -> found.map(MetadataIndexerCatalogReader::toEntry));
	}

	private static IndexerCatalogEntry toEntry(IndexerRecord record) {
		return new IndexerCatalogEntry(
			record.id(),
			record.uid(),
			record.targetId(),
			record.targetName(),
			record.indexName(),
			record.queueName(),
			record.type(),
			record.role(),
			record.indexOwnership(),
			record.status(),
			record.provisioningState(),
			record.runtimeState(),
			record.mutationState(),
			record.createdAt(),
			record.updatedAt(),
			record.version()
		);
	}
}
