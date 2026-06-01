package com.inqwise.indexer.hot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.commands.RoutedIndexActions;
import com.inqwise.indexer.metadata.TargetPeriod;
import com.inqwise.indexer.metadata.TargetPeriodResolver;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;

public class HotTarget {
	private static final String NONE_PERIOD_KEY = "";

	private final Integer targetDefinitionId;
	private final String targetUid;
	private final String targetName;
	private final TargetPeriodStrategy periodStrategy;
	private final Map<String, HotConcreteTarget> concreteTargetsByPeriodKey;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();

	public HotTarget(
		Integer targetDefinitionId,
		String targetUid,
		String targetName,
		TargetPeriodStrategy periodStrategy,
		List<HotConcreteTarget> concreteTargets
	) {
		this.targetDefinitionId = Objects.requireNonNull(targetDefinitionId, "targetDefinitionId");
		this.targetUid = targetUid;
		this.targetName = Objects.requireNonNull(targetName, "targetName");
		this.periodStrategy = periodStrategy == null ? TargetPeriodStrategy.NONE : periodStrategy;
		this.concreteTargetsByPeriodKey = concreteTargetsByPeriodKey(concreteTargets);
	}

	public Integer targetDefinitionId() {
		return targetDefinitionId;
	}

	public String targetUid() {
		return targetUid;
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
			.map(HotIndexer::id)
			.toList();
	}

	public List<HotIndexer> indexers() {
		return concreteTargetsByPeriodKey.values().stream()
			.flatMap(target -> target.liveWriters().stream())
			.toList();
	}

	public HotRouteResult route(HotIndexActionsRequest request) {
		if (request.actions().isEmpty()) {
			return new HotRouteResult.Miss("No actions submitted");
		}

		if (!matchesTarget(request)) {
			return new HotRouteResult.Miss("Request target does not match hot target: " + targetName);
		}

		TargetPeriod period;
		try {
			period = periodResolver.resolve(periodStrategy, request.timestamp());
		} catch (RuntimeException error) {
			return new HotRouteResult.Miss(error.getMessage());
		}

		HotConcreteTarget concreteTarget = concreteTargetsByPeriodKey.get(periodKey(period.key()));
		if (concreteTarget == null) {
			return new HotRouteResult.Miss("Concrete hot target not found: " + period.key());
		}

		if (concreteTarget.liveWriters().isEmpty()) {
			return new HotRouteResult.Miss("No hot live writers for target: " + concreteTarget.targetId());
		}

		Map<HotIndexer, List<IndexerActionItem>> actionsByIndexer = new LinkedHashMap<>();
		for (IndexerActionItem action : request.actions()) {
			if (!routeAction(concreteTarget, actionsByIndexer, action)) {
				return new HotRouteResult.Miss("Action was not accepted by hot live writers");
			}
		}

		return new HotRouteResult.Routed(actionsByIndexer.entrySet().stream()
			.map(entry -> new RoutedIndexActions(
				entry.getKey().id(),
				0L,
				entry.getKey().queueName(),
				entry.getValue()
			))
			.toList());
	}

	private boolean routeAction(
		HotConcreteTarget concreteTarget,
		Map<HotIndexer, List<IndexerActionItem>> actionsByIndexer,
		IndexerActionItem action
	) {
		boolean accepted = false;
		for (HotIndexer indexer : concreteTarget.liveWriters()) {
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
		if (request.targetUid() != null && targetUid != null && !request.targetUid().equals(targetUid)) {
			return false;
		}

		if (request.targetName() != null && !request.targetName().equals(targetName)) {
			return false;
		}

		return request.targetUid() != null || request.targetName() != null;
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
