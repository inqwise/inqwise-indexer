package com.inqwise.indexer.load;

import io.vertx.core.Future;

public interface LoadProviderRegistry {
	Future<LoadProvider> get(String providerId);
}
