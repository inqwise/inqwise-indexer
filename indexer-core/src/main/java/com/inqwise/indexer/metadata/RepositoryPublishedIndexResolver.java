package com.inqwise.indexer.metadata;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.targets.TargetCatalogQuery;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;

import io.vertx.core.Future;

public class RepositoryPublishedIndexResolver implements PublishedIndexResolver {
	private final DocumentStoreMetadataRepository repository;

	public RepositoryPublishedIndexResolver(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	@Override
	public Future<List<PublishedIndex>> resolvePublishedIndexes(PublishedIndexQuery query) {
		Objects.requireNonNull(query, "query");

		TargetCatalogQuery targetQuery = new TargetCatalogQuery(
			null,
			List.of(query.targetName()),
			List.of(TargetStatus.ACTIVE),
			List.of(TargetProvisioningState.READY)
		);

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
		IndexerMetadataQuery indexerQuery = new IndexerMetadataQuery(
			null,
			targetIds,
			List.of(IndexerType.INDEX),
			null,
			List.of(IndexerStatus.AVAILABLE),
			List.of(IndexerProvisioningState.READY),
			null,
			List.of(PublicationState.PUBLISHED),
			List.of(MutationState.WRITABLE, MutationState.READ_ONLY)
		);

		return repository.listIndexers(indexerQuery)
			.map(indexers -> indexers.stream()
				.sorted(Comparator
					.comparingInt((IndexerRecord indexer) -> targetOrder.get(indexer.targetId()))
					.thenComparing(IndexerRecord::id))
				.map(indexer -> new PublishedIndex(
					indexer.id(),
					indexer.targetId(),
					indexer.indexName()
				))
				.collect(Collectors.collectingAndThen(
					Collectors.toMap(
						PublishedIndex::indexName,
						Function.identity(),
						(existing, duplicate) -> existing,
						LinkedHashMap::new
					),
					indexesByName -> List.copyOf(indexesByName.values())
				)));
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
