package com.inqwise.indexer.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerLifecycleProviderSignal;
import com.inqwise.indexer.lifecycle.IndexerLifecycleSubscription;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public final class IndexerRuntimeReconciler {
	private static final Logger logger = LogManager.getLogger(IndexerRuntimeReconciler.class);

	private final DocumentStoreMetadataRepository repository;
	private final IndexerLifecycleEventBus eventBus;
	private final IndexerRuntime runtime;
	private final Vertx vertx;
	private final int maxDirtyIndexers;
	private final long safetySyncIntervalMs;
	private final Map<Integer, Long> dirtyVersions = new HashMap<>();
	private IndexerLifecycleSubscription indexerSubscription;
	private IndexerLifecycleSubscription providerSignalSubscription;
	private Future<Void> startFuture;
	private Future<Void> taskTail = Future.succeededFuture();
	private Long safetyTimerId;
	private Function<Throwable, Future<Void>> failureHandler = error -> Future.succeededFuture();
	private boolean accepting;
	private boolean dirtyTaskScheduled;
	private boolean failed;
	private boolean fullSynchronizationRunning;
	private boolean fullSynchronizationRequired;

	public IndexerRuntimeReconciler(
		Vertx vertx,
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus,
		IndexerRuntime runtime
	) {
		this(vertx, repository, eventBus, runtime, new IndexerRuntimeReconcilerOptions());
	}

	public IndexerRuntimeReconciler(
		Vertx vertx,
		DocumentStoreMetadataRepository repository,
		IndexerLifecycleEventBus eventBus,
		IndexerRuntime runtime,
		IndexerRuntimeReconcilerOptions options
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		IndexerRuntimeReconcilerOptions validated = Objects.requireNonNull(options, "options")
			.validate();
		this.maxDirtyIndexers = validated.getMaxDirtyIndexers();
		this.safetySyncIntervalMs = validated.getSafetySyncIntervalMs();
	}

	public synchronized Future<Void> start() {
		if (startFuture != null) {
			return startFuture;
		}

		accepting = true;
		failed = false;
		Future<Void> starting = eventBus.subscribe(this::markDirty)
			.compose(created -> {
				synchronized (this) {
					indexerSubscription = created;
				}
				return eventBus.subscribeProviderSignals(this::requestFullSynchronization);
			})
			.compose(created -> {
				synchronized (this) {
					providerSignalSubscription = created;
				}
				return enqueue(() -> synchronizeAll().compose(ignored -> drainPending()));
			})
			.onSuccess(ignored -> scheduleSafetySynchronization());
		startFuture = starting;
		starting.onFailure(error -> closeFailedStart());
		return starting;
	}

	public Future<Void> reconcile(Integer indexerId) {
		Objects.requireNonNull(indexerId, "indexerId");
		Future<Void> reconciliation = enqueue(() -> reconcile(indexerId, null));
		reconciliation.onFailure(this::handleReconciliationFailure);
		return reconciliation;
	}

	public Future<Void> fullSynchronize() {
		Future<Void> synchronization = enqueue(
			() -> synchronizeAll().compose(ignored -> drainPending())
		);
		synchronization.onFailure(this::handleReconciliationFailure);
		return synchronization;
	}

	public synchronized void onFailure(
		Function<Throwable, Future<Void>> failureHandler
	) {
		this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
	}

	private Future<Void> synchronizeAll() {
		synchronized (this) {
			fullSynchronizationRunning = true;
		}
		return repository.listRuntimeActiveIndexers()
			.compose(records -> {
				List<IndexerRecord> desired = records.stream()
					.filter(IndexerRuntimeReconciler::runtimeEligible)
					.sorted(Comparator.comparing(IndexerRecord::id))
					.toList();
				Set<Integer> desiredIds = new HashSet<>();
				Future<Void> synchronization = Future.succeededFuture();
				for (IndexerRecord record : desired) {
					desiredIds.add(record.id());
					synchronization = synchronization.compose(
						ignored -> runtime.reconcile(record)
					);
				}

				List<Integer> obsoleteIds = new ArrayList<>(runtime.indexerIds());
				obsoleteIds.removeAll(desiredIds);
				obsoleteIds.sort(Integer::compareTo);
				for (Integer indexerId : obsoleteIds) {
					synchronization = synchronization.compose(
						ignored -> runtime.close(indexerId)
					);
				}
				return synchronization;
			})
			.onComplete(ignored -> clearFullSynchronizationRunning());
	}

	public Future<Void> stop() {
		IndexerLifecycleSubscription closingIndexer;
		IndexerLifecycleSubscription closingProviderSignals;
		synchronized (this) {
			accepting = false;
			cancelSafetySynchronization();
			dirtyVersions.clear();
			fullSynchronizationRequired = false;
			dirtyTaskScheduled = false;
			closingIndexer = indexerSubscription;
			closingProviderSignals = providerSignalSubscription;
			indexerSubscription = null;
			providerSignalSubscription = null;
			startFuture = null;
		}
		Future<Void> unsubscribed = closeSubscriptions(
			closingProviderSignals,
			closingIndexer
		);
		return unsubscribed.transform(subscriptionResult -> enqueue(runtime::stop)
			.transform(runtimeResult -> firstFailure(subscriptionResult, runtimeResult)));
	}

	private void requestFullSynchronization(IndexerLifecycleProviderSignal signal) {
		Objects.requireNonNull(signal, "signal");
		requestFullSynchronization(false);
	}

	private void requestSafetySynchronization() {
		requestFullSynchronization(true);
	}

	private void requestFullSynchronization(boolean onlyIfNotPendingOrRunning) {
		boolean schedule;
		synchronized (this) {
			if (!accepting) {
				return;
			}
			if (onlyIfNotPendingOrRunning
				&& (fullSynchronizationRequired || fullSynchronizationRunning)) {
				return;
			}
			dirtyVersions.clear();
			fullSynchronizationRequired = true;
			schedule = schedulePendingIfStarted();
		}
		if (schedule) {
			enqueue(this::drainPending).onFailure(this::handleReconciliationFailure);
		}
	}

	private void markDirty(IndexerMetadataChanged event) {
		boolean schedule;
		synchronized (this) {
			if (!accepting) {
				return;
			}
			if (!fullSynchronizationRequired) {
				Long currentVersion = dirtyVersions.get(event.getIndexerId());
				if (currentVersion != null) {
					dirtyVersions.put(
						event.getIndexerId(),
						Math.max(currentVersion, event.getVersion())
					);
				} else if (dirtyVersions.size() >= maxDirtyIndexers) {
					dirtyVersions.clear();
					fullSynchronizationRequired = true;
				} else {
					dirtyVersions.put(event.getIndexerId(), event.getVersion());
				}
			}
			schedule = schedulePendingIfStarted();
		}
		if (schedule) {
			enqueue(this::drainPending).onFailure(this::handleReconciliationFailure);
		}
	}

	private Future<Void> drainPending() {
		PendingWork work;
		synchronized (this) {
			work = new PendingWork(fullSynchronizationRequired, Map.copyOf(dirtyVersions));
			fullSynchronizationRequired = false;
			dirtyVersions.clear();
		}

		Future<Void> drained = work.fullSynchronization()
			? synchronizeAll()
			: Future.succeededFuture();
		if (!work.fullSynchronization()) {
			for (Map.Entry<Integer, Long> entry : work.dirtyVersions().entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.toList()) {
				drained = drained.compose(
					ignored -> reconcile(entry.getKey(), entry.getValue())
				);
			}
		}
		return drained.compose(ignored -> {
			boolean repeat;
			synchronized (this) {
				repeat = fullSynchronizationRequired || !dirtyVersions.isEmpty();
				if (!repeat) {
					dirtyTaskScheduled = false;
				}
			}
			return repeat ? drainPending() : Future.succeededFuture();
		}).onFailure(error -> restorePending(work));
	}

	private Future<Void> reconcile(Integer indexerId, Long observedVersion) {
		return load(indexerId, observedVersion, false)
			.compose(found -> found == null ? runtime.close(indexerId) : runtime.reconcile(found));
	}

	private Future<IndexerRecord> load(
		Integer indexerId,
		Long observedVersion,
		boolean retried
	) {
		return repository.getIndexerById(indexerId).compose(found -> {
			IndexerRecord record = found.orElse(null);
			if (observedVersion != null && record != null
				&& record.version() < observedVersion) {
				if (!retried) {
					return load(indexerId, observedVersion, true);
				}
				return Future.failedFuture(new RetryableStaleStateException(
					"Indexer metadata version " + record.version()
						+ " is behind observed version " + observedVersion
						+ " for indexer " + indexerId
				));
			}
			return Future.succeededFuture(record);
		});
	}

	private synchronized Future<Void> enqueue(Supplier<Future<Void>> task) {
		Future<Void> execution = taskTail
			.recover(error -> Future.succeededFuture())
			.compose(ignored -> task.get());
		taskTail = execution;
		return execution;
	}

	private Future<Void> closeFailedStart() {
		IndexerLifecycleSubscription closingIndexer;
		IndexerLifecycleSubscription closingProviderSignals;
		synchronized (this) {
			cancelSafetySynchronization();
			closingIndexer = indexerSubscription;
			closingProviderSignals = providerSignalSubscription;
			indexerSubscription = null;
			providerSignalSubscription = null;
			startFuture = null;
			accepting = false;
			dirtyVersions.clear();
			fullSynchronizationRequired = false;
			dirtyTaskScheduled = false;
		}
		return closeSubscriptions(closingProviderSignals, closingIndexer);
	}

	private synchronized void scheduleSafetySynchronization() {
		if (!accepting || safetyTimerId != null) {
			return;
		}
		safetyTimerId = vertx.setPeriodic(
			safetySyncIntervalMs,
			ignored -> requestSafetySynchronization()
		);
	}

	private synchronized void clearFullSynchronizationRunning() {
		fullSynchronizationRunning = false;
	}

	private void cancelSafetySynchronization() {
		if (safetyTimerId != null) {
			vertx.cancelTimer(safetyTimerId);
			safetyTimerId = null;
		}
	}

	private boolean schedulePendingIfStarted() {
		boolean schedule = startFuture != null
			&& startFuture.isComplete()
			&& !dirtyTaskScheduled;
		if (schedule) {
			dirtyTaskScheduled = true;
		}
		return schedule;
	}

	private Future<Void> close(IndexerLifecycleSubscription closing) {
		return closing == null ? Future.succeededFuture() : closing.close();
	}

	private Future<Void> closeSubscriptions(
		IndexerLifecycleSubscription providerSignals,
		IndexerLifecycleSubscription indexers
	) {
		return close(providerSignals).transform(providerResult -> close(indexers)
			.transform(indexerResult -> firstFailure(providerResult, indexerResult)));
	}

	private Future<Void> firstFailure(
		AsyncResult<Void> first,
		AsyncResult<Void> second
	) {
		if (first.failed()) {
			return Future.failedFuture(first.cause());
		}
		return second.failed()
			? Future.failedFuture(second.cause())
			: Future.succeededFuture();
	}

	private synchronized void restorePending(PendingWork work) {
		if (!accepting) {
			return;
		}
		if (work.fullSynchronization()) {
			fullSynchronizationRequired = true;
			dirtyVersions.clear();
		} else if (!fullSynchronizationRequired) {
			work.dirtyVersions().forEach(
				(id, version) -> dirtyVersions.merge(id, version, Math::max)
			);
		}
		dirtyTaskScheduled = false;
	}

	private void handleReconciliationFailure(Throwable error) {
		if (causedByStaleState(error)) {
			logger.warn("Indexer runtime metadata remained stale after immediate reload", error);
			return;
		}

		Function<Throwable, Future<Void>> handler;
		synchronized (this) {
			if (failed || !accepting) {
				return;
			}
			failed = true;
			accepting = false;
			handler = failureHandler;
		}
		logger.error("Indexer runtime reconciliation failed; entering recovery-only mode", error);
		stop()
			.transform(stopResult -> handler.apply(error)
				.transform(handlerResult -> firstFailure(stopResult, handlerResult)))
			.onFailure(handlerError -> logger.error(
				"Indexer runtime recovery-only transition failed",
				handlerError
			));
	}

	private boolean causedByStaleState(Throwable error) {
		for (Throwable cause = error; cause != null; cause = cause.getCause()) {
			if (cause instanceof RetryableStaleStateException) {
				return true;
			}
		}
		return false;
	}

	private static boolean runtimeEligible(IndexerRecord record) {
		return record.status() == IndexerStatus.AVAILABLE
			&& record.provisioningState() == IndexerProvisioningState.READY
			&& record.runtimeState() == IndexerRuntimeState.ACTIVE
			&& record.mutationState() != MutationState.DELETING;
	}

	private record PendingWork(
		boolean fullSynchronization,
		Map<Integer, Long> dirtyVersions
	) {
	}
}
