package com.inqwise.indexer.load;

import java.util.List;

import com.inqwise.indexer.IndexerActionItem;

import io.vertx.core.Future;

public interface LoadWriter {
	Future<Void> submit(List<IndexerActionItem> items);

	Future<Void> complete(LoadCompletion completion);

	Future<Void> fail(Throwable error);
}
