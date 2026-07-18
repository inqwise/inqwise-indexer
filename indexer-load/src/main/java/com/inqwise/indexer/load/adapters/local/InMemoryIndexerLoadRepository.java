package com.inqwise.indexer.load.adapters.local;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.repository.AttachLiveWriterRequest;
import com.inqwise.indexer.load.repository.AttachLiveWriterResult;
import com.inqwise.indexer.load.repository.IndexerLoadCompletion;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.InsertIndexerLoad;
import com.inqwise.indexer.load.repository.RequestIndexerLoadBarrier;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadApproval;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadBarrier;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadFailure;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadState;


import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;

public class InMemoryIndexerLoadRepository implements IndexerLoadRepository {
	private final Map<Integer, IndexerLoadRecord> loadsByIndexerId = new ConcurrentHashMap<>();
	private final Map<Integer, IndexerLoadCompletion> completionsByIndexerId = new ConcurrentHashMap<>();

	@Override
	public synchronized Future<Void> insert(InsertIndexerLoad load) {
		try {
			require(load.indexerId(), "indexerId");
			require(load.targetId(), "targetId");
			if (loadsByIndexerId.containsKey(load.indexerId())) {
				throw new IllegalStateException("Indexer load already exists: " + load.indexerId());
			}
			if (completionsByIndexerId.containsKey(load.indexerId())) {
				throw new IllegalStateException("Indexer load was already completed: " + load.indexerId());
			}
			if (findActiveByTargetId(load.targetId()).isPresent()) {
				throw new IllegalStateException("Active indexer load already exists for target: " + load.targetId());
			}

			Instant now = Instant.now();
			loadsByIndexerId.put(load.indexerId(), IndexerLoadRecord.builder()
				.withIndexerId(load.indexerId())
				.withTargetId(load.targetId())
				.withLiveIndexerId(load.liveIndexerId())
				.withLiveWriterPolicy(load.liveWriterPolicy() == null
					? LiveWriterPolicy.NONE
					: load.liveWriterPolicy())
				.withProviderId(require(load.providerId(), "providerId"))
				.withState(load.state() == null ? IndexerLoadState.CREATED : load.state())
				.withReloadStartAt(load.reloadStartAt())
				.withLiveReplayFrom(load.liveReplayFrom())
				.withSourceFrom(load.sourceFrom())
				.withSourceTo(load.sourceTo())
				.withSourceQuery(load.sourceQuery())
				.withSourcePlaybookId(load.sourcePlaybookId())
				.withReviewRequired(load.reviewRequired())
				.withCreatedAt(now)
				.withUpdatedAt(now)
				.withVersion(0L)
				.build());

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
	public Future<Optional<IndexerLoadCompletion>> getCompletionByIndexerId(Integer indexerId) {
		return Future.succeededFuture(Optional.ofNullable(completionsByIndexerId.get(indexerId)));
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
				existing.liveIndexerId(),
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
				existing.liveIndexerId(),
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
	public synchronized Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(
		AttachLiveWriterRequest request
	) {
		try {
			IndexerLoadRecord existing = requireLoad(request.indexerId());
			require(request.liveIndexerId(), "liveIndexerId");
			if (!isActive(existing.state())) {
				throw new IllegalStateException("Indexer load is not active: " + existing.state());
			}
			if (existing.liveIndexerId() != null) {
				return Future.succeededFuture(new AttachLiveWriterResult(
					existing.liveIndexerId().equals(request.liveIndexerId()),
					existing.liveIndexerId(),
					existing.version()
				));
			}
			requireVersion(existing, request.expectedVersion());

			IndexerLoadRecord updated = copy(
				existing,
				request.liveIndexerId(),
				existing.state(),
				existing.approvedAt(),
				existing.approvedBy(),
				existing.approvalReason(),
				existing.lastBarrierId(),
				existing.lastBarrierTimestamp(),
				existing.lastBarrierReachedAt(),
				existing.failureReason(),
				existing.failedAt()
			);
			loadsByIndexerId.put(request.indexerId(), updated);
			return Future.succeededFuture(new AttachLiveWriterResult(
				true,
				request.liveIndexerId(),
				updated.version()
			));
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public synchronized Future<Void> requestBarrier(RequestIndexerLoadBarrier request) {
		try {
			IndexerLoadRecord existing = requireLoad(request.indexerId(), request.expectedVersion());
			if (existing.liveIndexerId() == null) {
				throw new IllegalStateException("Indexer load has no live writer: " + existing.indexerId());
			}
			if (existing.state() != IndexerLoadState.HISTORICAL_COMPLETE) {
				throw new IllegalStateException(
					"Indexer load is not ready to request catch-up barrier: " + existing.state()
				);
			}

			loadsByIndexerId.put(request.indexerId(), copy(
				existing,
				existing.liveIndexerId(),
				IndexerLoadState.CATCH_UP_BARRIER_REQUESTED,
				existing.approvedAt(),
				existing.approvedBy(),
				existing.approvalReason(),
				require(request.barrierId(), "barrierId"),
				require(request.barrierTimestamp(), "barrierTimestamp"),
				null,
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
			if (existing.state() != IndexerLoadState.CATCH_UP_BARRIER_REQUESTED) {
				throw new IllegalStateException(
					"Indexer load has no requested catch-up barrier: " + existing.state()
				);
			}
			if (!Objects.equals(existing.lastBarrierId(), update.barrierId())
				|| !Objects.equals(existing.lastBarrierTimestamp(), update.barrierTimestamp())) {
				throw new IllegalStateException(
					"Catch-up barrier does not match requested barrier: " + update.indexerId()
				);
			}
			loadsByIndexerId.put(update.indexerId(), copy(
				existing,
				existing.liveIndexerId(),
				existing.reviewRequired()
					? IndexerLoadState.WAITING_FOR_REVIEW
					: IndexerLoadState.CATCH_UP_READY,
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
				existing.liveIndexerId(),
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
	public synchronized Future<Void> finalizeCleanup(Integer indexerId, long expectedVersion) {
		try {
			IndexerLoadRecord existing = requireLoad(indexerId, expectedVersion);
			if (existing.state() != IndexerLoadState.CANCELLED
				&& existing.state() != IndexerLoadState.PUBLISHED) {
				throw new IllegalStateException("Indexer load is not cleanup-ready: " + existing.state());
			}
			completionsByIndexerId.put(indexerId, new IndexerLoadCompletion(
				indexerId,
				existing.state(),
				existing.version(),
				Instant.now()
			));
			loadsByIndexerId.remove(indexerId);
			return Future.succeededFuture();
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	private IndexerLoadRecord copy(
		IndexerLoadRecord existing,
		Integer liveIndexerId,
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
		return IndexerLoadRecord.builder()
			.from(existing)
			.withLiveIndexerId(liveIndexerId)
			.withState(state)
			.withApprovedAt(approvedAt)
			.withApprovedBy(approvedBy)
			.withApprovalReason(approvalReason)
			.withLastBarrierId(lastBarrierId)
			.withLastBarrierTimestamp(lastBarrierTimestamp)
			.withLastBarrierReachedAt(lastBarrierReachedAt)
			.withFailureReason(failureReason)
			.withFailedAt(failedAt)
			.withUpdatedAt(Instant.now())
			.withVersion(existing.version() + 1)
			.build();
	}

	private IndexerLoadRecord requireLoad(Integer indexerId, long expectedVersion) {
		IndexerLoadRecord existing = requireLoad(indexerId);
		requireVersion(existing, expectedVersion);
		return existing;
	}

	private IndexerLoadRecord requireLoad(Integer indexerId) {
		IndexerLoadRecord existing = loadsByIndexerId.get(indexerId);
		if (existing == null) {
			throw new IllegalStateException("Indexer load not found: " + indexerId);
		}
		return existing;
	}

	private void requireVersion(IndexerLoadRecord existing, long expectedVersion) {
		if (existing.version() != expectedVersion) {
			throw new IllegalStateException(
				"Indexer load version conflict for id " + existing.indexerId() + ": expected "
					+ expectedVersion + " but was " + existing.version()
			);
		}
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

	private static <T> T require(T value, String name) {
		if (value == null) {
			throw new NullPointerException(name);
		}
		return value;
	}
}
