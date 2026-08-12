package com.inqwise.indexer.load.api;

import java.util.List;

import io.vertx.core.Future;

public interface LoadQuery {
	Future<List<IndexerLoadRecord>> list(int max);
}
