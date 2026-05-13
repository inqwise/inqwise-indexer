package com.inqwise.indexer;

import java.util.List;
import java.util.Optional;

import io.vertx.core.Future;

public interface IndexerRepository {
	Future<Integer> save(IndexerModel model);

	Future<Optional<IndexerModel>> get(Integer id);

	Future<List<IndexerModel>> getByTargetId(Integer targetId);

	Future<List<IndexerModel>> list();

	Future<Optional<IndexerModel>> updateStatus(Integer id, IndexerStatus status);

	Future<Boolean> delete(Integer id);
}
