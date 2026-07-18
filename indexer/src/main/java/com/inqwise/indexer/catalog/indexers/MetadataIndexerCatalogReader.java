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
			? IndexerCatalogQuery.builder().build()
			: query;
		return repository.listIndexers(IndexerMetadataQuery.builder()
			.withIds(resolved.ids())
			.withTargetIds(resolved.targetIds())
			.withTypes(resolved.types())
			.withRoles(resolved.roles())
			.withStatuses(resolved.statuses())
			.withProvisioningStates(resolved.provisioningStates())
			.withRuntimeStates(resolved.runtimeStates())
			.withMutationStates(resolved.mutationStates())
			.build()).map(records -> records.stream()
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
		return IndexerCatalogEntry.builder()
			.withId(record.id())
			.withUid(record.uid())
			.withTargetId(record.targetId())
			.withTargetName(record.targetName())
			.withIndexName(record.indexName())
			.withQueueName(record.queueName())
			.withType(record.type())
			.withRole(record.role())
			.withIndexOwnership(record.indexOwnership())
			.withStatus(record.status())
			.withProvisioningState(record.provisioningState())
			.withRuntimeState(record.runtimeState())
			.withMutationState(record.mutationState())
			.withCreatedAt(record.createdAt())
			.withUpdatedAt(record.updatedAt())
			.withVersion(record.version())
			.build();
	}
}
