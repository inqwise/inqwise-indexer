package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.catalog.targets.TargetCatalogQuery;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.publication.PublishedIndexQuery;
import com.inqwise.indexer.publication.PublishedIndexResolver;

import io.vertx.core.Future;

public class RepositoryPublishedIndexResolver implements PublishedIndexResolver {
	private final DocumentStoreMetadataRepository repository;

	public RepositoryPublishedIndexResolver(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public Future<List<PublishedIndex>> resolvePublishedIndexes(PublishedIndexQuery query) {
		Objects.requireNonNull(query, "query");

		TargetCatalogQuery targetQuery = TargetCatalogQuery.builder()
			.withTargetNames(List.of(query.targetName()))
			.withStatuses(List.of(TargetStatus.ACTIVE))
			.withProvisioningStates(List.of(TargetProvisioningState.READY))
			.build();

		return repository.listTargets(targetQuery)
			.map(targets -> targets.stream()
				.filter(target -> overlaps(target, query))
				.sorted(targetOrder())
				.toList())
			.compose(targets -> resolveIndexes(targets));
	}

	private Future<List<PublishedIndex>> resolveIndexes(List<TargetRecord> targets) {
		if (targets.isEmpty()) {
			return Future.succeededFuture(List.of());
		}

		List<Integer> targetIds = targets.stream().map(TargetRecord::id).toList();
		Map<Integer, Integer> targetOrder = new HashMap<>();
		for (int index = 0; index < targetIds.size(); index++) {
			targetOrder.put(targetIds.get(index), index);
		}
		IndexerMetadataQuery indexerQuery = IndexerMetadataQuery.builder()
			.withTargetIds(targetIds)
			.withTypes(List.of(IndexerType.INDEX))
			.withStatuses(List.of(IndexerStatus.AVAILABLE))
			.withProvisioningStates(List.of(IndexerProvisioningState.READY))
			.withPublicationStates(List.of(PublicationState.PUBLISHED))
			.withMutationStates(List.of(MutationState.WRITABLE, MutationState.READ_ONLY))
			.build();

		return repository.listIndexers(indexerQuery)
			.map(indexers -> indexers.stream()
				.sorted(Comparator
					.comparingInt((IndexerRecord indexer) -> targetOrder.get(indexer.targetId()))
					.thenComparing(IndexerRecord::id))
				.toList())
			.compose(this::resolveSchemas)
			.map(indexes -> indexes.stream().collect(Collectors.collectingAndThen(
					Collectors.toMap(
						PublishedIndex::indexName,
						Function.identity(),
						(existing, duplicate) -> existing,
						LinkedHashMap::new
					),
					indexesByName -> List.copyOf(indexesByName.values())
				)));
	}

	private Future<List<PublishedIndex>> resolveSchemas(List<IndexerRecord> indexers) {
		Future<List<PublishedIndex>> resolved = Future.succeededFuture(new ArrayList<>());
		for (IndexerRecord indexer : indexers) {
			resolved = resolved.compose(indexes -> repository
				.getActiveManifestByIndexerId(indexer.id())
				.compose(found -> found
					.map(manifest -> {
						indexes.add(PublishedIndex.builder()
							.withIndexerId(indexer.id())
							.withTargetId(indexer.targetId())
							.withIndexName(indexer.indexName())
							.withSchemaName(manifest.schemaName())
							.withSchemaVersion(manifest.schemaVersion())
							.build());
						return Future.succeededFuture(indexes);
					})
					.orElseGet(() -> Future.failedFuture(
						"Published indexer has no active manifest: " + indexer.id()
					))));
		}
		return resolved.map(List::copyOf);
	}

	private boolean overlaps(TargetRecord target, PublishedIndexQuery query) {
		Instant start = target.periodStartInclusive();
		Instant end = target.periodEndExclusive();
		if (start == null && end == null) {
			return true;
		}

		return start != null
			&& end != null
			&& start.isBefore(query.toExclusive())
			&& end.isAfter(query.fromInclusive());
	}

	private Comparator<TargetRecord> targetOrder() {
		return Comparator
			.comparing(
				TargetRecord::periodStartInclusive,
				Comparator.nullsFirst(Comparator.naturalOrder())
			)
			.thenComparing(TargetRecord::id);
	}
}
