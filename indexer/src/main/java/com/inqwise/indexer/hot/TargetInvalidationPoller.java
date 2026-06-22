package com.inqwise.indexer.hot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class TargetInvalidationPoller {
	private static final Logger logger = LogManager.getLogger(TargetInvalidationPoller.class);

	private final Vertx vertx;
	private final TargetInvalidationRegistry registry;
	private final HotMetadataView hotMetadataView;
	private final TargetInvalidationRegistryOptions options;
	private final Map<Integer, TargetInvalidationEntry> observedByTargetId = new HashMap<>();
	private Future<Void> activePoll = Future.succeededFuture();
	private Long timerId;
	private boolean started;
	private boolean polling;

	public TargetInvalidationPoller(
		Vertx vertx,
		TargetInvalidationRegistry registry,
		HotMetadataView hotMetadataView,
		TargetInvalidationRegistryOptions options
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.registry = Objects.requireNonNull(registry, "registry");
		this.hotMetadataView = Objects.requireNonNull(hotMetadataView, "hotMetadataView");
		this.options = Objects.requireNonNull(options, "options");
	}

	public synchronized Future<Void> start() {
		if (started) {
			return activePoll;
		}

		started = true;
		polling = true;
		activePoll = pollNow()
			.onSuccess(ignored -> schedule())
			.onFailure(ignored -> resetAfterStartFailure())
			.onComplete(ignored -> clearPolling());
		return activePoll;
	}

	public synchronized Future<Void> stop() {
		started = false;
		if (timerId != null) {
			vertx.cancelTimer(timerId);
			timerId = null;
		}

		return activePoll.recover(ignored -> Future.succeededFuture());
	}

	public synchronized boolean isStarted() {
		return started;
	}

	Future<Void> pollNow() {
		return registry.listInvalidations(options.maxTargets())
			.map(entries -> {
				apply(entries);
				return null;
			});
	}

	private synchronized void schedule() {
		if (!started || timerId != null) {
			return;
		}

		timerId = vertx.setPeriodic(options.pollInterval().toMillis(), ignored -> pollOnTimer());
	}

	synchronized void pollOnTimer() {
		if (!started || polling) {
			return;
		}

		polling = true;
		activePoll = pollNow().onComplete(result -> {
			if (result.failed()) {
				logger.error("Target invalidation poll failed", result.cause());
			}
			clearPolling();
		});
	}

	private synchronized void apply(TargetInvalidationEntries invalidations) {
		if (invalidations.truncated()) {
			hotMetadataView.invalidateAllHotTargets();
			observedByTargetId.clear();
			return;
		}

		Set<Integer> currentTargetIds = new HashSet<>();
		for (TargetInvalidationEntry entry : invalidations.entries()) {
			currentTargetIds.add(entry.concreteTargetId());
			TargetInvalidationEntry observed = observedByTargetId.put(
				entry.concreteTargetId(),
				entry
			);
			if (!entry.equals(observed)) {
				hotMetadataView.invalidateHotTargetByConcreteTargetId(
					entry.concreteTargetId()
				);
			}
		}

		observedByTargetId.keySet().retainAll(currentTargetIds);
	}

	private synchronized void resetAfterStartFailure() {
		started = false;
		timerId = null;
	}

	private synchronized void clearPolling() {
		polling = false;
	}
}
