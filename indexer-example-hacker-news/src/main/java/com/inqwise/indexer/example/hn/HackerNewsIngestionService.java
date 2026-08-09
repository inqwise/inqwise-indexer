package com.inqwise.indexer.example.hn;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.service.action.TargetActionService;
import com.inqwise.indexer.service.action.TargetActionSubmitRequest;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public final class HackerNewsIngestionService {
	private static final Logger logger = LogManager.getLogger(HackerNewsIngestionService.class);

	private final Vertx vertx;
	private final HackerNewsClient client;
	private final TargetActionService targetActions;
	private final HackerNewsDocumentProjector projector;
	private final HackerNewsOptions options;
	private final Map<Long, String> fingerprints = new LinkedHashMap<>();
	private final AtomicBoolean pollInFlight = new AtomicBoolean();

	private Long timerId;
	private Future<Void> activePoll = Future.succeededFuture();
	private boolean running;
	private long pollsCompleted;
	private long pollsSkipped;
	private long itemsFetched;
	private long itemFetchFailures;
	private long actionsSubmitted;
	private long unchangedItems;
	private Instant lastPollStartedAt;
	private Instant lastPollCompletedAt;
	private String lastError;

	public HackerNewsIngestionService(
		Vertx vertx,
		HackerNewsClient client,
		TargetActionService targetActions,
		HackerNewsDocumentProjector projector,
		HackerNewsOptions options
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.client = Objects.requireNonNull(client, "client");
		this.targetActions = Objects.requireNonNull(targetActions, "targetActions");
		this.projector = Objects.requireNonNull(projector, "projector");
		this.options = Objects.requireNonNull(options, "options");
	}

	public synchronized Future<Void> start() {
		if (running) {
			return Future.succeededFuture();
		}
		running = true;
		timerId = vertx.setPeriodic(options.pollInterval().toMillis(), ignored -> runScheduledPoll());
		logger.info(
			"Hacker News ingestion started: target={}, poll_interval_ms={}, max_changes_per_poll={}",
			HackerNewsConsumer.TARGET_NAME,
			options.pollInterval().toMillis(),
			options.maxChangesPerPoll()
		);
		runScheduledPoll();
		return Future.succeededFuture();
	}

	public synchronized Future<Void> stop() {
		running = false;
		if (timerId != null) {
			vertx.cancelTimer(timerId);
			timerId = null;
		}
		return activePoll.recover(error -> Future.succeededFuture()).onComplete(ignored -> {
			HackerNewsIngestionStatus current = status();
			logger.info(
				"Hacker News ingestion stopped: polls_completed={}, actions_submitted={}",
				current.pollsCompleted(),
				current.actionsSubmitted()
			);
		});
	}

	public Future<Void> pollOnce() {
		if (!pollInFlight.compareAndSet(false, true)) {
			synchronized (this) {
				pollsSkipped++;
			}
			return Future.succeededFuture();
		}

		synchronized (this) {
			lastPollStartedAt = Instant.now();
			lastError = null;
		}
		Future<Void> poll = client.fetchUpdates()
			.map(this::changedItemIds)
			.compose(this::fetchProjections)
			.compose(this::submitChanged)
			.onSuccess(ignored -> {
				synchronized (this) {
					pollsCompleted++;
					lastPollCompletedAt = Instant.now();
				}
			})
			.onFailure(error -> {
				synchronized (this) {
					lastError = error.getMessage();
				}
			})
			.eventually(() -> {
				pollInFlight.set(false);
				return Future.succeededFuture();
			});
		synchronized (this) {
			activePoll = poll;
		}
		return poll;
	}

	public synchronized HackerNewsIngestionStatus status() {
		return HackerNewsIngestionStatus.builder()
			.withRunning(running)
			.withPollInFlight(pollInFlight.get())
			.withPollsCompleted(pollsCompleted)
			.withPollsSkipped(pollsSkipped)
			.withItemsFetched(itemsFetched)
			.withItemFetchFailures(itemFetchFailures)
			.withActionsSubmitted(actionsSubmitted)
			.withUnchangedItems(unchangedItems)
			.withLastPollStartedAt(lastPollStartedAt)
			.withLastPollCompletedAt(lastPollCompletedAt)
			.withLastError(lastError)
			.build();
	}

	private void runScheduledPoll() {
		pollOnce().onFailure(error -> logger.warn("Hacker News poll failed", error));
	}

	private List<Long> changedItemIds(HackerNewsUpdates updates) {
		Set<Long> unique = new LinkedHashSet<>(updates.itemIds());
		List<Long> selected = unique.stream().limit(options.maxChangesPerPoll()).toList();
		synchronized (this) {
			fingerprints.keySet().retainAll(selected);
		}
		return selected;
	}

	private Future<List<HackerNewsProjection>> fetchProjections(List<Long> ids) {
		Future<List<HackerNewsProjection>> result = Future.succeededFuture(new ArrayList<>());
		for (int offset = 0; offset < ids.size(); offset += options.requestConcurrency()) {
			int from = offset;
			int to = Math.min(ids.size(), offset + options.requestConcurrency());
			result = result.compose(projections -> fetchWindow(ids.subList(from, to))
				.map(window -> {
					projections.addAll(window);
					return projections;
				}));
		}
		return result.map(List::copyOf);
	}

	private Future<List<HackerNewsProjection>> fetchWindow(List<Long> ids) {
		List<Future<Optional<HackerNewsProjection>>> fetches = ids.stream()
			.map(this::fetchProjection)
			.toList();
		return Future.join(fetches).map(ignored -> fetches.stream()
			.map(Future::result)
			.flatMap(Optional::stream)
			.toList());
	}

	private Future<Optional<HackerNewsProjection>> fetchProjection(long id) {
		return client.fetchItem(id)
			.map(item -> {
				synchronized (this) {
					itemsFetched++;
				}
				return item.map(projector::project);
			})
			.recover(error -> {
				synchronized (this) {
					itemFetchFailures++;
					lastError = "Item " + id + ": " + error.getMessage();
				}
				logger.debug("Unable to fetch Hacker News item {}", id, error);
				return Future.succeededFuture(Optional.empty());
			});
	}

	private Future<Void> submitChanged(List<HackerNewsProjection> projections) {
		List<HackerNewsProjection> changed = projections.stream()
			.filter(this::isChanged)
			.toList();
		synchronized (this) {
			unchangedItems += projections.size() - changed.size();
		}

		Future<Void> submitted = Future.succeededFuture();
		for (int offset = 0; offset < changed.size(); offset += options.actionBatchSize()) {
			int from = offset;
			int to = Math.min(changed.size(), offset + options.actionBatchSize());
			List<HackerNewsProjection> batch = changed.subList(from, to);
			submitted = submitted.compose(ignored -> submitBatch(batch));
		}
		return submitted;
	}

	private synchronized boolean isChanged(HackerNewsProjection projection) {
		return !projection.fingerprint().equals(fingerprints.get(projection.itemId()));
	}

	private Future<Void> submitBatch(List<HackerNewsProjection> batch) {
		List<IndexerActionItem> actions = batch.stream()
			.map(HackerNewsProjection::action)
			.toList();
		String submissionId = "hn-" + batch.get(0).itemId() + "-" + batch.get(batch.size() - 1).itemId();
		return targetActions.submit(TargetActionSubmitRequest.builder()
			.withSubmissionId(submissionId)
			.withTargetName(HackerNewsConsumer.TARGET_NAME)
			.withActions(actions)
			.build()).<Void>mapEmpty().onSuccess(ignored -> {
				synchronized (this) {
					batch.forEach(projection ->
						fingerprints.put(projection.itemId(), projection.fingerprint()));
					actionsSubmitted += batch.size();
				}
				logger.info(
					"Hacker News action batch accepted: submission_id={}, actions={}",
					submissionId,
					batch.size()
				);
			});
	}
}
