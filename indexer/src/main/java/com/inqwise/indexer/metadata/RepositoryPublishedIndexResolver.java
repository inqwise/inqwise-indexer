package com.inqwise.indexer.metadata;

import java.util.List;
import java.util.Objects;

import io.vertx.core.Future;

public class RepositoryPublishedIndexResolver implements PublishedIndexResolver {
	private final DocumentStoreMetadataRepository repository;

	public RepositoryPublishedIndexResolver(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public Future<List<PublishedIndex>> resolvePublishedIndexes(Integer targetId) {
		Objects.requireNonNull(targetId, "targetId");

		return repository.listPublishedIndexersByTargetId(targetId)
			.map(indexers -> indexers.stream()
				.filter(indexer -> indexer.mutationState() != MutationState.DELETING)
				.map(indexer -> new PublishedIndex(
					indexer.id(),
					indexer.targetId(),
					indexer.indexName()
				))
				.toList());
	}
}
