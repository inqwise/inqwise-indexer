package com.inqwise.indexer.hot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetDefinitionProvider;
import com.inqwise.indexer.catalog.targets.TargetCatalogQuery;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.providers.IndexerProviderQuery;
import com.inqwise.indexer.providers.IndexerProviders;
import com.inqwise.indexer.providers.HotIndexerCapability;

import io.vertx.core.Future;

public class DefaultHotMetadataView implements HotMetadataView {
	private final DocumentStoreMetadataRepository repository;
	private final TargetDefinitionProvider targetDefinitionProvider;
	private final IndexerProviders indexerProviders;
	private final ConcurrentMap<String, HotTarget> targetsByName = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, HotTarget> targetsByConcreteTargetId = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, HotIndexerCapability> indexersById = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, HotTarget> targetsByIndexerId = new ConcurrentHashMap<>();

	public DefaultHotMetadataView(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerProviders indexerProviders
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.targetDefinitionProvider = Objects.requireNonNull(
			targetDefinitionProvider,
			"targetDefinitionProvider"
		);
		this.indexerProviders = Objects.requireNonNull(indexerProviders, "indexerProviders");
	}

	@Override
	public Optional<HotTarget> findTargetByName(String targetName) {
		return Optional.ofNullable(targetsByName.get(targetName));
	}

	@Override
	public Optional<HotIndexerCapability> findIndexerById(Integer indexerId) {
		return Optional.ofNullable(indexersById.get(indexerId));
	}

	@Override
	public Future<Void> refreshHotTargetByConcreteTargetId(Integer targetId) {
		return repository.getTargetById(targetId)
			.compose(found -> found
				.map(this::refreshHotTarget)
				.orElseGet(() -> {
					invalidateHotTargetByConcreteTargetId(targetId);
					return Future.succeededFuture();
				}));
	}

	@Override
	public void invalidateHotTargetByConcreteTargetId(Integer targetId) {
		HotTarget target = targetsByConcreteTargetId.get(targetId);
		if (target != null) {
			remove(target);
		}
	}

	@Override
	public void invalidateHotTargetByIndexerId(Integer indexerId) {
		HotTarget target = targetsByIndexerId.get(indexerId);
		if (target != null) {
			remove(target);
			return;
		}

		indexersById.remove(indexerId);
	}

	@Override
	public synchronized void invalidateAllHotTargets() {
		targetsByName.clear();
		targetsByConcreteTargetId.clear();
		indexersById.clear();
		targetsByIndexerId.clear();
	}

	private Future<Void> refreshHotTarget(TargetRecord concreteTarget) {
		return targetDefinitionProvider.getByName(concreteTarget.targetName())
			.compose(found -> found
				.map(definition -> loadConcreteTargets(definition)
					.compose(targets -> buildHotTarget(definition, targets)))
				.orElseGet(() -> {
					invalidateHotTargetByConcreteTargetId(concreteTarget.id());
					return Future.succeededFuture();
				}));
	}

	private Future<List<TargetRecord>> loadConcreteTargets(TargetDefinition definition) {
		return repository.listTargets(TargetCatalogQuery.builder()
			.withTargetNames(List.of(definition.targetName()))
			.withStatuses(List.of(TargetStatus.ACTIVE))
			.withProvisioningStates(List.of(TargetProvisioningState.READY))
			.build());
	}

	private Future<Void> buildHotTarget(
		TargetDefinition definition,
		List<TargetRecord> concreteTargets
	) {
		if (concreteTargets.isEmpty()) {
			removeByName(definition.targetName());
			return Future.succeededFuture();
		}

		List<Integer> targetIds = concreteTargets.stream()
			.map(TargetRecord::id)
			.toList();

		return indexerProviders.listIndexers(IndexerProviderQuery.builder()
			.withTargetIds(targetIds)
			.withTypes(List.of(IndexerType.INDEX))
			.withRoles(List.of(IndexerRole.LIVE_WRITER))
			.withStatuses(List.of(IndexerStatus.AVAILABLE))
			.withProvisioningStates(List.of(IndexerProvisioningState.READY))
			.withRuntimeStates(List.of(IndexerRuntimeState.ACTIVE))
			.withMutationStates(List.of(MutationState.WRITABLE))
			.build()).map(resolvedIndexers -> {
			Map<Integer, List<HotIndexerCapability>> liveWritersByTargetId = resolvedIndexers.stream()
				.flatMap(resolved -> resolved.hotIndexer().stream())
				.collect(Collectors.groupingBy(
					HotIndexerCapability::targetId,
					LinkedHashMap::new,
					Collectors.toList()
				));

			HotTarget hotTarget = HotTarget.builder()
				.withTargetName(definition.targetName())
				.withPeriodStrategy(definition.periodStrategy())
				.withConcreteTargets(concreteTargets.stream()
					.map(target -> HotConcreteTarget.builder()
						.withTargetId(target.id())
						.withTargetName(target.targetName())
						.withPeriodKey(target.periodKey())
						.withPeriodStartInclusive(target.periodStartInclusive())
						.withPeriodEndExclusive(target.periodEndExclusive())
						.withLiveWriters(liveWritersByTargetId.getOrDefault(target.id(), List.of()))
						.build())
					.toList())
				.build();

			replace(hotTarget);
			return null;
		});
	}

	private synchronized void replace(HotTarget target) {
		removeByName(target.targetName());
		targetsByName.put(target.targetName(), target);

		for (Integer targetId : target.concreteTargetIds()) {
			targetsByConcreteTargetId.put(targetId, target);
		}

		for (HotIndexerCapability indexer : target.indexers()) {
			indexersById.put(indexer.id(), indexer);
			targetsByIndexerId.put(indexer.id(), target);
		}
	}

	private synchronized void remove(HotTarget target) {
		removeByName(target.targetName());
	}

	private synchronized void removeByName(String targetName) {
		List<HotTarget> matchingTargets = new ArrayList<>();
		for (HotTarget target : targetsByName.values()) {
			if (target.targetName().equals(targetName)) {
				matchingTargets.add(target);
			}
		}

		for (HotTarget target : matchingTargets) {
			targetsByName.remove(target.targetName(), target);
			for (Integer targetId : target.concreteTargetIds()) {
				targetsByConcreteTargetId.remove(targetId, target);
			}
			for (Integer indexerId : target.indexerIds()) {
				indexersById.remove(indexerId);
				targetsByIndexerId.remove(indexerId, target);
			}
		}
	}
}
