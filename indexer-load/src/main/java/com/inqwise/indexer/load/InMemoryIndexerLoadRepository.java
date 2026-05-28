package com.inqwise.indexer.load;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;

public class InMemoryIndexerLoadRepository implements IndexerLoadRepository {
	private final Map<Integer, IndexerLoadRecord> loadsByLoadIndexerId = new ConcurrentHashMap<>();

	@Override
	public synchronized Future<Void> insert(InsertIndexerLoad load) {
		try {
			require(load.loadIndexerId(), "loadIndexerId");
			if (loadsByLoadIndexerId.containsKey(load.loadIndexerId())) {
				throw new IllegalStateException("Indexer load already exists: " + load.loadIndexerId());
			}

			Instant now = Instant.now();
			loadsByLoadIndexerId.put(load.loadIndexerId(), new IndexerLoadRecord(
				load.loadIndexerId(),
				load.liveIndexerId(),
				load.state() == null ? IndexerLoadState.CREATED : load.state(),
				load.reloadStartAt(),
				load.liveReplayFrom(),
				load.reviewRequired(),
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
	public Future<Optional<IndexerLoadRecord>> getByLoadIndexerId(Integer loadIndexerId) {
		return Future.succeededFuture(Optional.ofNullable(loadsByLoadIndexerId.get(loadIndexerId)));
	}

	@Override
	public Future<Optional<IndexerLoadRecord>> getActiveByTargetIndexerId(Integer indexerId) {
		return Future.succeededFuture(loadsByLoadIndexerId.values().stream()
			.filter(load -> indexerId.equals(load.loadIndexerId()) || indexerId.equals(load.liveIndexerId()))
			.filter(load -> isActive(load.state()))
			.findFirst());
	}

	@Override
	public synchronized Future<Void> updateState(UpdateIndexerLoadState update) {
		try {
			IndexerLoadRecord existing = requireLoad(update.loadIndexerId(), update.expectedVersion());
			loadsByLoadIndexerId.put(update.loadIndexerId(), copy(
				existing,
				require(update.state(), "state"),
				existing.approvedAt(),
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
			IndexerLoadRecord existing = requireLoad(update.loadIndexerId(), update.expectedVersion());
			loadsByLoadIndexerId.put(update.loadIndexerId(), copy(
				existing,
				IndexerLoadState.CATCH_UP_READY,
				existing.approvedAt(),
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
			IndexerLoadRecord existing = requireLoad(update.loadIndexerId(), update.expectedVersion());
			loadsByLoadIndexerId.put(update.loadIndexerId(), copy(
				existing,
				IndexerLoadState.FAILED,
				existing.approvedAt(),
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
	public synchronized Future<Void> delete(Integer loadIndexerId, long expectedVersion) {
		try {
			requireLoad(loadIndexerId, expectedVersion);
			loadsByLoadIndexerId.remove(loadIndexerId);
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	private IndexerLoadRecord copy(
		IndexerLoadRecord existing,
		IndexerLoadState state,
		Instant approvedAt,
		String lastBarrierId,
		Instant lastBarrierTimestamp,
		Instant lastBarrierReachedAt,
		String failureReason,
		Instant failedAt
	) {
		return new IndexerLoadRecord(
			existing.loadIndexerId(),
			existing.liveIndexerId(),
			state,
			existing.reloadStartAt(),
			existing.liveReplayFrom(),
			existing.reviewRequired(),
			approvedAt,
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

	private IndexerLoadRecord requireLoad(Integer loadIndexerId, long expectedVersion) {
		IndexerLoadRecord existing = loadsByLoadIndexerId.get(loadIndexerId);
		if (existing == null) {
			throw new IllegalStateException("Indexer load not found: " + loadIndexerId);
		}
		if (existing.version() != expectedVersion) {
			throw new IllegalStateException(
				"Indexer load version conflict for id " + loadIndexerId + ": expected "
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

	private static <T> T require(T value, String name) {
		if (value == null) {
			throw new NullPointerException(name);
		}
		return value;
	}
}
