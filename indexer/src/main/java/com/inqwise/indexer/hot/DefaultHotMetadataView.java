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

import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.TargetDefinitionRecord;
import com.inqwise.indexer.metadata.TargetMetadataQuery;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.providers.IndexerProviderQuery;
import com.inqwise.indexer.providers.IndexerProviders;

import io.vertx.core.Future;

public class DefaultHotMetadataView implements HotMetadataView {
	private final DocumentStoreMetadataRepository repository;
	private final IndexerProviders indexerProviders;
	private final ConcurrentMap<String, HotTarget> targetsByName = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, HotTarget> targetsByUid = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, HotTarget> targetsByConcreteTargetId = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, HotIndexer> indexersById = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, HotTarget> targetsByIndexerId = new ConcurrentHashMap<>();

	public DefaultHotMetadataView(
		DocumentStoreMetadataRepository repository,
		IndexerProviders indexerProviders
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.indexerProviders = Objects.requireNonNull(indexerProviders, "indexerProviders");
	}

	@Override
	public Optional<HotTarget> findTargetByName(String targetName) {
		return Optional.ofNullable(targetsByName.get(targetName));
	}

	@Override
	public Optional<HotTarget> findTargetByUid(String targetUid) {
		return Optional.ofNullable(targetsByUid.get(targetUid));
	}

	@Override
	public Optional<HotIndexer> findIndexerById(Integer indexerId) {
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

	private Future<Void> refreshHotTarget(TargetRecord concreteTarget) {
		return repository.getTargetDefinitionById(concreteTarget.targetDefinitionId())
			.compose(found -> found
				.map(definition -> loadConcreteTargets(definition)
					.compose(targets -> buildHotTarget(definition, targets)))
				.orElseGet(() -> {
					invalidateHotTargetByConcreteTargetId(concreteTarget.id());
					return Future.succeededFuture();
				}));
	}

	private Future<List<TargetRecord>> loadConcreteTargets(TargetDefinitionRecord definition) {
		return repository.listTargets(new TargetMetadataQuery(
			null,
			List.of(definition.id()),
			List.of(TargetStatus.ACTIVE),
			List.of(TargetProvisioningState.READY)
		));
	}

	private Future<Void> buildHotTarget(
		TargetDefinitionRecord definition,
		List<TargetRecord> concreteTargets
	) {
		if (concreteTargets.isEmpty()) {
			removeByDefinition(definition);
			return Future.succeededFuture();
		}

		List<Integer> targetIds = concreteTargets.stream()
			.map(TargetRecord::id)
			.toList();

		return indexerProviders.listIndexers(new IndexerProviderQuery(
			null,
			targetIds,
			List.of(IndexerType.INDEX),
			List.of(IndexerRole.LIVE_WRITER),
			List.of(IndexerStatus.AVAILABLE),
			List.of(IndexerProvisioningState.READY),
			List.of(IndexerRuntimeState.ACTIVE),
			null,
			List.of(MutationState.WRITABLE)
		)).map(resolvedIndexers -> {
			Map<Integer, List<HotIndexer>> liveWritersByTargetId = resolvedIndexers.stream()
				.flatMap(resolved -> resolved.hotIndexer().stream())
				.collect(Collectors.groupingBy(
					HotIndexer::targetId,
					LinkedHashMap::new,
					Collectors.toList()
				));

			HotTarget hotTarget = new HotTarget(
				definition.id(),
				definition.uid(),
				definition.targetName(),
				definition.periodStrategy(),
				concreteTargets.stream()
					.map(target -> new HotConcreteTarget(
						target.id(),
						target.targetName(),
						target.periodKey(),
						target.periodStartInclusive(),
						target.periodEndExclusive(),
						liveWritersByTargetId.getOrDefault(target.id(), List.of())
					))
					.toList()
			);

			replace(hotTarget);
			return null;
		});
	}

	private synchronized void replace(HotTarget target) {
		removeByDefinition(target.targetDefinitionId());
		targetsByName.put(target.targetName(), target);
		if (target.targetUid() != null) {
			targetsByUid.put(target.targetUid(), target);
		}

		for (Integer targetId : target.concreteTargetIds()) {
			targetsByConcreteTargetId.put(targetId, target);
		}

		for (HotIndexer indexer : target.indexers()) {
			indexersById.put(indexer.id(), indexer);
			targetsByIndexerId.put(indexer.id(), target);
		}
	}

	private synchronized void remove(HotTarget target) {
		removeByDefinition(target.targetDefinitionId());
	}

	private synchronized void removeByDefinition(TargetDefinitionRecord definition) {
		removeByDefinition(definition.id());
	}

	private synchronized void removeByDefinition(Integer targetDefinitionId) {
		List<HotTarget> matchingTargets = new ArrayList<>();
		for (HotTarget target : targetsByName.values()) {
			if (target.targetDefinitionId().equals(targetDefinitionId)) {
				matchingTargets.add(target);
			}
		}

		for (HotTarget target : matchingTargets) {
			targetsByName.remove(target.targetName(), target);
			if (target.targetUid() != null) {
				targetsByUid.remove(target.targetUid(), target);
			}
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
