package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;


import java.util.Optional;
import java.util.List;

import io.vertx.core.Future;

public interface IndexerLoadRepository {
	Future<Void> insert(InsertIndexerLoad load);

	Future<Optional<IndexerLoadRecord>> getByIndexerId(Integer indexerId);

	Future<Optional<IndexerLoadCompletion>> getCompletionByIndexerId(Integer indexerId);

	Future<Optional<IndexerLoadRecord>> getActiveByTargetId(Integer targetId);

	Future<Optional<IndexerLoadRecord>> getActiveByTargetIndexerId(Integer indexerId);

	Future<List<IndexerLoadRecord>> list(int max);

	Future<Void> updateState(UpdateIndexerLoadState update);

	/** Returns the immutable intent retained until terminal cleanup is finalized. */
	Future<Optional<LoadPublication>> getPublication(Integer indexerId);

	/**
	 * Atomically records the publication intent at its expected load version and fences
	 * other load mutations. Exact replay succeeds; a different intent must fail.
	 * Recording the intent does not advance the load version. Only the PUBLISHED
	 * transition may follow it, advancing the version once before cleanup.
	 */
	Future<Void> preparePublication(LoadPublication publication);

	Future<Void> approve(UpdateIndexerLoadApproval update);

	Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(AttachLiveWriterRequest request);

	Future<Void> requestBarrier(RequestIndexerLoadBarrier request);

	Future<Void> markBarrierReached(UpdateIndexerLoadBarrier update);

	Future<Void> markFailed(UpdateIndexerLoadFailure update);

	Future<Void> finalizeCleanup(Integer indexerId, long expectedVersion);
}
