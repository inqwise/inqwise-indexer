package com.inqwise.indexer;

import io.vertx.core.Future;

public interface IndexerResourceCleaner {
	IndexerResourceCleaner NOOP = model -> Future.succeededFuture();

	Future<Void> clean(IndexerModel model);
}
