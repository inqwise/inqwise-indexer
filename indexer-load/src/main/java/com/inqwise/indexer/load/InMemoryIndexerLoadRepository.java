package com.inqwise.indexer.load;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;

public class InMemoryIndexerLoadRepository implements IndexerLoadRepository {
	private final Map<Integer, IndexerLoadRecord> loadsByIndexerId = new ConcurrentHashMap<>();

	@Override
	public synchronized Future<Void> insert(InsertIndexerLoad load) {
		try {
			require(load.indexerId(), "indexerId");
			require(load.targetId(), "targetId");
			if (loadsByIndexerId.containsKey(load.indexerId())) {
				throw new IllegalStateException("Indexer load already exists: " + load.indexerId());
			}
			if (findActiveByTargetId(load.targetId()).isPresent()) {
				throw new IllegalStateException("Active indexer load already exists for target: " + load.targetId());
			}

			Instant now = Instant.now();
			loadsByIndexerId.put(load.indexerId(), new IndexerLoadRecord(
				load.indexerId(),
				load.targetId(),
				load.liveIndexerId(),
				require(load.providerId(), "providerId"),
				load.state() == null ? IndexerLoadState.CREATED : load.state(),
				load.reloadStartAt(),
				load.liveReplayFrom(),
				load.sourceFrom(),
				load.sourceTo(),
				copy(load.sourceQuery()),
				load.sourcePlaybookId(),
				load.reviewRequired(),
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				now,
				now,
				0L
			));

			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public Future<Optional<IndexerLoadRecord>> getByIndexerId(Integer indexerId) {
		return Future.succeededFuture(Optional.ofNullable(loadsByIndexerId.get(indexerId)));
	}

	@Override
	public Future<Optional<IndexerLoadRecord>> getActiveByTargetId(Integer targetId) {
		return Future.succeededFuture(findActiveByTargetId(targetId));
	}

	@Override
	public Future<Optional<IndexerLoadRecord>> getActiveByTargetIndexerId(Integer indexerId) {
		return Future.succeededFuture(loadsByIndexerId.values().stream()
			.filter(load -> indexerId.equals(load.indexerId()) || indexerId.equals(load.liveIndexerId()))
			.filter(load -> isActive(load.state()))
			.findFirst());
	}

	@Override
	public synchronized Future<Void> updateState(UpdateIndexerLoadState update) {
		try {
			IndexerLoadRecord existing = requireLoad(update.indexerId(), update.expectedVersion());
			loadsByIndexerId.put(update.indexerId(), copy(
				existing,
				require(update.state(), "state"),
				existing.approvedAt(),
				existing.approvedBy(),
				existing.approvalReason(),
				existing.lastBarrierId(),
				existing.lastBarrierTimestamp(),
				existing.lastBarrierReachedAt(),
				existing.failureReason(),
				existing.failedAt()
			));
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> approve(UpdateIndexerLoadApproval update) {
		try {
			IndexerLoadRecord existing = requireLoad(update.indexerId(), update.expectedVersion());
			loadsByIndexerId.put(update.indexerId(), copy(
				existing,
				IndexerLoadState.APPROVED,
				update.approvedAt() == null ? Instant.now() : update.approvedAt(),
				update.approvedBy(),
				update.approvalReason(),
				existing.lastBarrierId(),
				existing.lastBarrierTimestamp(),
				existing.lastBarrierReachedAt(),
				existing.failureReason(),
				existing.failedAt()
			));
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> markBarrierReached(UpdateIndexerLoadBarrier update) {
		try {
			IndexerLoadRecord existing = requireLoad(update.indexerId(), update.expectedVersion());
			loadsByIndexerId.put(update.indexerId(), copy(
				existing,
				IndexerLoadState.CATCH_UP_READY,
				existing.approvedAt(),
				existing.approvedBy(),
				existing.approvalReason(),
				require(update.barrierId(), "barrierId"),
				require(update.barrierTimestamp(), "barrierTimestamp"),
				update.reachedAt() == null ? Instant.now() : update.reachedAt(),
				existing.failureReason(),
				existing.failedAt()
			));
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> markFailed(UpdateIndexerLoadFailure update) {
		try {
			IndexerLoadRecord existing = requireLoad(update.indexerId(), update.expectedVersion());
			loadsByIndexerId.put(update.indexerId(), copy(
				existing,
				IndexerLoadState.FAILED,
				existing.approvedAt(),
				existing.approvedBy(),
				existing.approvalReason(),
				existing.lastBarrierId(),
				existing.lastBarrierTimestamp(),
				existing.lastBarrierReachedAt(),
				require(update.failureReason(), "failureReason"),
				update.failedAt() == null ? Instant.now() : update.failedAt()
			));
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> delete(Integer indexerId, long expectedVersion) {
		try {
			requireLoad(indexerId, expectedVersion);
			loadsByIndexerId.remove(indexerId);
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	private IndexerLoadRecord copy(
		IndexerLoadRecord existing,
		IndexerLoadState state,
		Instant approvedAt,
		String approvedBy,
		String approvalReason,
		String lastBarrierId,
		Instant lastBarrierTimestamp,
		Instant lastBarrierReachedAt,
		String failureReason,
		Instant failedAt
	) {
		return new IndexerLoadRecord(
			existing.indexerId(),
			existing.targetId(),
			existing.liveIndexerId(),
			existing.providerId(),
			state,
			existing.reloadStartAt(),
			existing.liveReplayFrom(),
			existing.sourceFrom(),
			existing.sourceTo(),
			copy(existing.sourceQuery()),
			existing.sourcePlaybookId(),
			existing.reviewRequired(),
			approvedAt,
			approvedBy,
			approvalReason,
			lastBarrierId,
			lastBarrierTimestamp,
			lastBarrierReachedAt,
			failureReason,
			failedAt,
			existing.createdAt(),
			Instant.now(),
			existing.version() + 1
		);
	}

	private IndexerLoadRecord requireLoad(Integer indexerId, long expectedVersion) {
		IndexerLoadRecord existing = loadsByIndexerId.get(indexerId);
		if (existing == null) {
			throw new IllegalStateException("Indexer load not found: " + indexerId);
		}
		if (existing.version() != expectedVersion) {
			throw new IllegalStateException(
				"Indexer load version conflict for id " + indexerId + ": expected "
					+ expectedVersion + " but was " + existing.version()
			);
		}
		return existing;
	}

	private boolean isActive(IndexerLoadState state) {
		return state != IndexerLoadState.PUBLISHED
			&& state != IndexerLoadState.FAILED
			&& state != IndexerLoadState.CANCELLED;
	}

	private Optional<IndexerLoadRecord> findActiveByTargetId(Integer targetId) {
		return loadsByIndexerId.values().stream()
			.filter(load -> targetId.equals(load.targetId()))
			.filter(load -> isActive(load.state()))
			.findFirst();
	}

	private io.vertx.core.json.JsonObject copy(io.vertx.core.json.JsonObject json) {
		return json == null ? null : json.copy();
	}

	private static <T> T require(T value, String name) {
		if (value == null) {
			throw new NullPointerException(name);
		}
		return value;
	}
}
