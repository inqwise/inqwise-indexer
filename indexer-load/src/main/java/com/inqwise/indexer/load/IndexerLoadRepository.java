package com.inqwise.indexer.load;

import java.util.Optional;

import io.vertx.core.Future;

public interface IndexerLoadRepository {
	Future<Void> insert(InsertIndexerLoad load);

	Future<Optional<IndexerLoadRecord>> getByLoadIndexerId(Integer loadIndexerId);

	Future<Optional<IndexerLoadRecord>> getActiveByTargetIndexerId(Integer indexerId);

	Future<Void> updateState(UpdateIndexerLoadState update);

	Future<Void> markBarrierReached(UpdateIndexerLoadBarrier update);

	Future<Void> markFailed(UpdateIndexerLoadFailure update);

	Future<Void> delete(Integer loadIndexerId, long expectedVersion);
}
