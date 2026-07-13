package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;


import java.util.Optional;

import io.vertx.core.Future;

public interface IndexerLoadRepository {
	Future<Void> insert(InsertIndexerLoad load);

	Future<Optional<IndexerLoadRecord>> getByIndexerId(Integer indexerId);

	Future<Optional<IndexerLoadCompletion>> getCompletionByIndexerId(Integer indexerId);

	Future<Optional<IndexerLoadRecord>> getActiveByTargetId(Integer targetId);

	Future<Optional<IndexerLoadRecord>> getActiveByTargetIndexerId(Integer indexerId);

	Future<Void> updateState(UpdateIndexerLoadState update);

	Future<Void> approve(UpdateIndexerLoadApproval update);

	Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(AttachLiveWriterRequest request);

	Future<Void> requestBarrier(RequestIndexerLoadBarrier request);

	Future<Void> markBarrierReached(UpdateIndexerLoadBarrier update);

	Future<Void> markFailed(UpdateIndexerLoadFailure update);

	Future<Void> finalizeCleanup(Integer indexerId, long expectedVersion);
}
