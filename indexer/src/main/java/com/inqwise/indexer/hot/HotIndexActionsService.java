package com.inqwise.indexer.hot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.routing.ActionDestination;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.routing.RoutedIndexActions;
import com.inqwise.indexer.routing.SubmitIndexActionsCommand;
import com.inqwise.indexer.routing.IndexerPublishingService;
import com.inqwise.indexer.routing.IndexerPublishingRouteException;
import com.inqwise.indexer.routing.InvalidRouteCache;
import com.inqwise.indexer.routing.InvalidRouteRecord;
import com.inqwise.indexer.routing.InvalidRouteSignature;
import com.inqwise.indexer.providers.HotIndexerCapability;

import io.vertx.core.Future;

public class HotIndexActionsService {
	private final HotMetadataView hotMetadataView;
	private final IndexerPublishingService publisher;
	private final CommandService commandService;
	private final InvalidRouteCache invalidRouteCache;

	public HotIndexActionsService(
		HotMetadataView hotMetadataView,
		IndexerPublishingService publisher,
		CommandService commandService
	) {
		this(hotMetadataView, publisher, commandService, null);
	}

	public HotIndexActionsService(
		HotMetadataView hotMetadataView,
		IndexerPublishingService publisher,
		CommandService commandService,
		InvalidRouteCache invalidRouteCache
	) {
		this.hotMetadataView = Objects.requireNonNull(hotMetadataView, "hotMetadataView");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
		this.commandService = Objects.requireNonNull(commandService, "commandService");
		this.invalidRouteCache = invalidRouteCache;
	}

	public Future<Void> submit(HotIndexActionsRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.actions().isEmpty()) {
			return Future.failedFuture("No actions submitted");
		}

		Optional<InvalidRouteRecord> invalidRoute = findInvalidRoute(request);
		if (invalidRoute.isPresent()) {
			return Future.failedFuture(CommandFailure.stableInvalid(
				"Invalid route cached: " + invalidRoute.get().reason()
			));
		}

		return route(request)
			.compose(result -> submitRouteResult(request, result));
	}

	private Future<Void> submitRouteResult(
		HotIndexActionsRequest request,
		HotRouteResult result
	) {
		if (result instanceof HotRouteResult.Routed routed) {
			return publisher.publish(routed.groups())
				.recover(error -> recoverRoutedPublishFailure(request, routed, error));
		}

		return fallback(request);
	}

	private Future<Void> recoverRoutedPublishFailure(
		HotIndexActionsRequest request,
		HotRouteResult.Routed routed,
		Throwable error
	) {
		if (!isRoutePublishRejection(error)) {
			return Future.failedFuture(error);
		}

		invalidateRoutedTargets(routed.groups());
		return fallback(request);
	}

	private void invalidateRoutedTargets(List<RoutedIndexActions> groups) {
		for (RoutedIndexActions group : groups) {
			hotMetadataView.invalidateHotTargetByConcreteTargetId(group.targetId());
		}
	}

	private Future<HotRouteResult> route(HotIndexActionsRequest request) {
		if (hasTargetEnvelope(request)) {
			return routeByTarget(request);
		}

		return Future.succeededFuture(routeDirect(request));
	}

	private Future<HotRouteResult> routeByTarget(HotIndexActionsRequest request) {
		Optional<HotTarget> target = hotMetadataView.findTargetByName(request.targetName());
		if (target.isPresent()) {
			return Future.succeededFuture(target.get().route(request));
		}

		return hotMetadataView.refreshHotTargetByName(request.targetName())
			.map(loaded -> loaded
				.<HotRouteResult>map(found -> found.route(request))
				.orElseGet(() -> HotRouteResult.Miss.builder()
					.withReason("Hot target not found")
					.build()));
	}

	private HotRouteResult routeDirect(HotIndexActionsRequest request) {
		Map<HotIndexerCapability, List<IndexerActionItem>> actionsByIndexer = new LinkedHashMap<>();

		for (IndexerActionItem action : request.actions()) {
			ActionDestination destination = ActionDestination.from(action);
			if (destination.indexerId() == null) {
				return HotRouteResult.Miss.builder()
					.withReason("Direct hot route requires indexer id")
					.build();
			}

			HotIndexerCapability indexer = hotMetadataView.findIndexerById(
				destination.indexerId()
			).orElse(null);
			if (indexer == null) {
				return HotRouteResult.Miss.builder()
					.withReason("Hot indexer not found: " + destination.indexerId())
					.build();
			}

			IndexerActionItem routed = indexer.route(action, IndexerActionRouteMode.DIRECT)
				.orElseThrow(() -> new IllegalArgumentException(
					"Action is not accepted by hot indexer: " + destination.indexerId()
				));
			actionsByIndexer.computeIfAbsent(indexer, ignored -> new ArrayList<>())
				.add(routed);
		}

		return HotRouteResult.Routed.builder()
			.withGroups(actionsByIndexer.entrySet().stream()
				.map(entry -> RoutedIndexActions.builder()
					.withIndexerId(entry.getKey().id())
					.withTargetId(entry.getKey().targetId())
					.withIndexerVersion(entry.getKey().version())
					.withQueueName(entry.getKey().queueName())
					.withActions(entry.getValue())
					.build())
				.toList())
			.build();
	}

	private Future<Void> fallback(HotIndexActionsRequest request) {
		try {
			SubmitIndexActionsCommand command = fallbackCommand(request);
			return commandService.submit(command).recover(error -> {
				recordStableInvalidRoute(request, error);
				return Future.failedFuture(error);
			});
		} catch (RuntimeException error) {
			recordStableInvalidRoute(request, error);
			return Future.failedFuture(error);
		}
	}

	private SubmitIndexActionsCommand fallbackCommand(HotIndexActionsRequest request) {
		if (hasTargetEnvelope(request)) {
			return SubmitIndexActionsCommand.builder()
				.withTargetName(request.targetName())
				.withTimestamp(request.timestamp())
				.withActions(request.actions())
				.build();
		}

		if (request.timestamp() != null) {
			throw new IllegalArgumentException("Timestamp is allowed only with target envelope routing");
		}

		return SubmitIndexActionsCommand.builder()
			.withActions(request.actions())
			.build();
	}

	private boolean hasTargetEnvelope(HotIndexActionsRequest request) {
		return request.targetName() != null;
	}

	private Optional<InvalidRouteRecord> findInvalidRoute(HotIndexActionsRequest request) {
		if (invalidRouteCache == null) {
			return Optional.empty();
		}

		return invalidRouteSignatures(request, true).stream()
			.map(invalidRouteCache::find)
			.filter(Optional::isPresent)
			.map(Optional::get)
			.findFirst();
	}

	private void recordStableInvalidRoute(HotIndexActionsRequest request, Throwable error) {
		if (invalidRouteCache == null || !isStableInvalid(error)) {
			return;
		}

		for (InvalidRouteSignature signature : invalidRouteSignatures(request, false)) {
			invalidRouteCache.record(signature, error.getMessage());
		}
	}

	private List<InvalidRouteSignature> invalidRouteSignatures(
		HotIndexActionsRequest request,
		boolean includeBroadTargetEnvelope
	) {
		if (!hasTargetEnvelope(request)) {
			return InvalidRouteSignatures.from(request);
		}

		Optional<HotTarget> target = hotMetadataView.findTargetByName(request.targetName());
		if (target.isEmpty()) {
			return includeBroadTargetEnvelope
				? InvalidRouteSignatures.from(request)
				: List.of();
		}

		try {
			List<InvalidRouteSignature> periodSignatures = InvalidRouteSignatures.from(
				request,
				target.get().resolvePeriodKey(request.timestamp())
			);
			if (!includeBroadTargetEnvelope) {
				return periodSignatures;
			}

			List<InvalidRouteSignature> signatures = new ArrayList<>(periodSignatures);
			signatures.addAll(InvalidRouteSignatures.from(request));
			return signatures;
		} catch (RuntimeException error) {
			return includeBroadTargetEnvelope
				? InvalidRouteSignatures.from(request)
				: List.of();
		}
	}

	private boolean isStableInvalid(Throwable error) {
		return error instanceof CommandFailure failure && failure.stableInvalid();
	}

	private boolean isRoutePublishRejection(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof IndexerPublishingRouteException) {
				return true;
			}
			current = current.getCause();
		}

		return false;
	}
}
