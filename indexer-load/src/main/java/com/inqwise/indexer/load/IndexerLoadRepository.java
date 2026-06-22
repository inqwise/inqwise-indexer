package com.inqwise.indexer.load;

import java.util.Optional;

import io.vertx.core.Future;

public interface IndexerLoadRepository {
	Future<Void> insert(InsertIndexerLoad load);

	Future<Optional<IndexerLoadRecord>> getByIndexerId(Integer indexerId);

	Future<Optional<IndexerLoadRecord>> getActiveByTargetId(Integer targetId);

	Future<Optional<IndexerLoadRecord>> getActiveByTargetIndexerId(Integer indexerId);

	Future<Void> updateState(UpdateIndexerLoadState update);

	Future<Void> approve(UpdateIndexerLoadApproval update);

	Future<AttachLiveWriterResult> attachLiveWriterIfAbsent(AttachLiveWriterRequest request);

	Future<Void> requestBarrier(RequestIndexerLoadBarrier request);

	Future<Void> markBarrierReached(UpdateIndexerLoadBarrier update);

	Future<Void> markFailed(UpdateIndexerLoadFailure update);

	Future<Void> delete(Integer indexerId, long expectedVersion);
}
