package com.inqwise.indexer.providers;

import java.util.List;
import java.util.Optional;

import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerMetadataQuery;

import io.vertx.core.Future;

public class MetadataIndexerProvider implements IndexerProvider {
	private final DocumentStoreMetadataRepository repository;

	public MetadataIndexerProvider(DocumentStoreMetadataRepository repository) {
		this.repository = repository;
	}

	@Override
	public IndexerType type() {
		return IndexerType.INDEX;
	}

	@Override
	public Future<Optional<ResolvedIndexer>> getIndexerById(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.map(found -> found.map(MetadataResolvedIndexer::new));
	}

	@Override
	public Future<List<ResolvedIndexer>> listIndexers(IndexerProviderQuery query) {
		return repository.listIndexers(toMetadataQuery(query))
			.map(indexers -> indexers.stream()
				.<ResolvedIndexer>map(MetadataResolvedIndexer::new)
				.toList());
	}

	private IndexerMetadataQuery toMetadataQuery(IndexerProviderQuery query) {
		if (query == null) {
			return null;
		}

		return new IndexerMetadataQuery(
			query.ids(),
			query.targetIds(),
			query.types(),
			query.roles(),
			query.statuses(),
			query.provisioningStates(),
			query.runtimeStates(),
			query.publicationStates(),
			query.mutationStates()
		);
	}
}
