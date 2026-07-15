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
import com.inqwise.indexer.commands.ActionDestination;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.RoutedIndexActions;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.routing.RoutedIndexActionPublisher;
import com.inqwise.indexer.routing.InvalidRouteCache;
import com.inqwise.indexer.routing.InvalidRouteRecord;
import com.inqwise.indexer.routing.InvalidRouteSignature;
import com.inqwise.indexer.providers.HotIndexerCapability;

import io.vertx.core.Future;

public class HotIndexActionsService {
	private final HotMetadataView hotMetadataView;
	private final RoutedIndexActionPublisher publisher;
	private final CommandService commandService;
	private final InvalidRouteCache invalidRouteCache;

	public HotIndexActionsService(
		HotMetadataView hotMetadataView,
		RoutedIndexActionPublisher publisher,
		CommandService commandService
	) {
		this(hotMetadataView, publisher, commandService, null);
	}

	public HotIndexActionsService(
		HotMetadataView hotMetadataView,
		RoutedIndexActionPublisher publisher,
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

		HotRouteResult result = route(request);
		if (result instanceof HotRouteResult.Routed routed) {
			return publisher.publish(routed.groups());
		}

		return fallback(request);
	}

	private HotRouteResult route(HotIndexActionsRequest request) {
		if (hasTargetEnvelope(request)) {
			return routeByTarget(request);
		}

		return routeDirect(request);
	}

	private HotRouteResult routeByTarget(HotIndexActionsRequest request) {
		Optional<HotTarget> target = hotMetadataView.findTargetByName(request.targetName());

		return target
			.<HotRouteResult>map(found -> found.route(request))
			.orElseGet(() -> new HotRouteResult.Miss("Hot target not found"));
	}

	private HotRouteResult routeDirect(HotIndexActionsRequest request) {
		Map<HotIndexerCapability, List<IndexerActionItem>> actionsByIndexer = new LinkedHashMap<>();

		for (IndexerActionItem action : request.actions()) {
			ActionDestination destination = ActionDestination.from(action);
			if (destination.indexerId() == null) {
				return new HotRouteResult.Miss("Direct hot route requires indexer id");
			}

			HotIndexerCapability indexer = hotMetadataView.findIndexerById(
				destination.indexerId()
			).orElse(null);
			if (indexer == null) {
				return new HotRouteResult.Miss("Hot indexer not found: " + destination.indexerId());
			}

			IndexerActionItem routed = indexer.route(action, IndexerActionRouteMode.DIRECT)
				.orElseThrow(() -> new IllegalArgumentException(
					"Action is not accepted by hot indexer: " + destination.indexerId()
				));
			actionsByIndexer.computeIfAbsent(indexer, ignored -> new ArrayList<>())
				.add(routed);
		}

		return new HotRouteResult.Routed(actionsByIndexer.entrySet().stream()
			.map(entry -> new RoutedIndexActions(
				entry.getKey().id(),
				entry.getKey().targetId(),
				0L,
				entry.getKey().queueName(),
				entry.getValue()
			))
			.toList());
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
			return new SubmitIndexActionsCommand(
				request.targetName(),
				request.timestamp(),
				request.actions()
			);
		}

		if (request.timestamp() != null) {
			throw new IllegalArgumentException("Timestamp is allowed only with target envelope routing");
		}

		return new SubmitIndexActionsCommand(request.actions());
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
}
