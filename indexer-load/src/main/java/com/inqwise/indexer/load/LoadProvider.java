package com.inqwise.indexer.load;

import io.vertx.core.Future;

public interface LoadProvider {
	Future<Void> start(LoadRequest request, LoadWriter writer);

	Future<Void> stop(LoadStopRequest request);
}
