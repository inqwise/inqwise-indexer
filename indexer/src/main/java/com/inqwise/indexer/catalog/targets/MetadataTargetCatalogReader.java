package com.inqwise.indexer.catalog.targets;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.TargetRecord;

import io.vertx.core.Future;

public final class MetadataTargetCatalogReader implements TargetCatalogReader {
	private final DocumentStoreMetadataRepository repository;

	public MetadataTargetCatalogReader(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public Future<List<TargetCatalogEntry>> list(TargetCatalogQuery query) {
		return repository.listTargets(query).map(records -> records.stream()
			.map(MetadataTargetCatalogReader::toEntry)
			.toList());
	}

	@Override
	public Future<Optional<TargetCatalogEntry>> findById(Integer id) {
		return repository.getTargetById(id).map(found -> found.map(MetadataTargetCatalogReader::toEntry));
	}

	@Override
	public Future<Optional<TargetCatalogEntry>> findByUid(String uid) {
		return repository.getTargetByUid(uid).map(found -> found.map(MetadataTargetCatalogReader::toEntry));
	}

	private static TargetCatalogEntry toEntry(TargetRecord record) {
		return TargetCatalogEntry.builder()
			.withId(record.id())
			.withUid(record.uid())
			.withTargetName(record.targetName())
			.withPeriodKey(record.periodKey())
			.withPeriodStartInclusive(record.periodStartInclusive())
			.withPeriodEndExclusive(record.periodEndExclusive())
			.withStatus(record.status())
			.withProvisioningState(record.provisioningState())
			.withCreatedAt(record.createdAt())
			.withUpdatedAt(record.updatedAt())
			.withVersion(record.version())
			.build();
	}
}
