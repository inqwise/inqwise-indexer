package com.inqwise.indexer.hot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.routing.RoutedIndexActions;
import com.inqwise.indexer.catalog.targets.TargetPeriod;
import com.inqwise.indexer.catalog.targets.TargetPeriodResolver;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.providers.HotIndexerCapability;

public class HotTarget {
	private static final String NONE_PERIOD_KEY = "";

	private final String targetName;
	private final TargetPeriodStrategy periodStrategy;
	private final Map<String, HotConcreteTarget> concreteTargetsByPeriodKey;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();

	public HotTarget(
		String targetName,
		TargetPeriodStrategy periodStrategy,
		List<HotConcreteTarget> concreteTargets
	) {
		this.targetName = Objects.requireNonNull(targetName, "targetName");
		this.periodStrategy = periodStrategy == null ? TargetPeriodStrategy.NONE : periodStrategy;
		this.concreteTargetsByPeriodKey = concreteTargetsByPeriodKey(concreteTargets);
	}

	public String targetName() {
		return targetName;
	}

	public List<Integer> concreteTargetIds() {
		return concreteTargetsByPeriodKey.values().stream()
			.map(HotConcreteTarget::targetId)
			.toList();
	}

	public List<Integer> indexerIds() {
		return indexers().stream()
			.map(HotIndexerCapability::id)
			.toList();
	}

	public List<HotIndexerCapability> indexers() {
		return concreteTargetsByPeriodKey.values().stream()
			.flatMap(target -> target.liveWriters().stream())
			.toList();
	}

	public String resolvePeriodKey(Instant timestamp) {
		return periodResolver.resolve(periodStrategy, timestamp).key();
	}

	public HotRouteResult route(HotIndexActionsRequest request) {
		if (request.actions().isEmpty()) {
			return HotRouteResult.Miss.builder()
				.withReason("No actions submitted")
				.build();
		}

		if (!matchesTarget(request)) {
			return HotRouteResult.Miss.builder()
				.withReason("Request target does not match hot target: " + targetName)
				.build();
		}

		TargetPeriod period;
		try {
			period = periodResolver.resolve(periodStrategy, request.timestamp());
		} catch (RuntimeException error) {
			return HotRouteResult.Miss.builder()
				.withReason(error.getMessage())
				.build();
		}

		HotConcreteTarget concreteTarget = concreteTargetsByPeriodKey.get(periodKey(period.key()));
		if (concreteTarget == null) {
			return HotRouteResult.Miss.builder()
				.withReason("Concrete hot target not found: " + period.key())
				.build();
		}

		if (concreteTarget.liveWriters().isEmpty()) {
			return HotRouteResult.Miss.builder()
				.withReason("No hot live writers for target: " + concreteTarget.targetId())
				.build();
		}

		Map<HotIndexerCapability, List<IndexerActionItem>> actionsByIndexer = new LinkedHashMap<>();
		for (IndexerActionItem action : request.actions()) {
			if (!routeAction(concreteTarget, actionsByIndexer, action)) {
				return HotRouteResult.Miss.builder()
					.withReason("Action was not accepted by hot live writers")
					.build();
			}
		}

		return HotRouteResult.Routed.builder()
			.withGroups(actionsByIndexer.entrySet().stream()
				.map(entry -> RoutedIndexActions.builder()
					.withIndexerId(entry.getKey().id())
					.withTargetId(entry.getKey().targetId())
					.withIndexerVersion(0L)
					.withQueueName(entry.getKey().queueName())
					.withActions(entry.getValue())
					.build())
				.toList())
			.build();
	}

	private boolean routeAction(
		HotConcreteTarget concreteTarget,
		Map<HotIndexerCapability, List<IndexerActionItem>> actionsByIndexer,
		IndexerActionItem action
	) {
		boolean accepted = false;
		for (HotIndexerCapability indexer : concreteTarget.liveWriters()) {
			Optional<IndexerActionItem> routed = indexer.route(action, IndexerActionRouteMode.CANDIDATE);
			if (routed.isPresent()) {
				actionsByIndexer.computeIfAbsent(indexer, ignored -> new ArrayList<>())
					.add(routed.get());
				accepted = true;
			}
		}

		return accepted;
	}

	private boolean matchesTarget(HotIndexActionsRequest request) {
		if (request.targetName() != null && !request.targetName().equals(targetName)) {
			return false;
		}

		return request.targetName() != null;
	}

	private Map<String, HotConcreteTarget> concreteTargetsByPeriodKey(
		List<HotConcreteTarget> concreteTargets
	) {
		Map<String, HotConcreteTarget> result = new LinkedHashMap<>();
		for (HotConcreteTarget concreteTarget : Objects.requireNonNull(
			concreteTargets,
			"concreteTargets"
		)) {
			result.put(periodKey(concreteTarget.periodKey()), concreteTarget);
		}

		return Map.copyOf(result);
	}

	private String periodKey(String periodKey) {
		return periodKey == null ? NONE_PERIOD_KEY : periodKey;
	}
}
