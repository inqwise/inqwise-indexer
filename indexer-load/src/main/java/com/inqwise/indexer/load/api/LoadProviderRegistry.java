package com.inqwise.indexer.load.api;

import io.vertx.core.Future;

public interface LoadProviderRegistry {
	Future<LoadProvider> get(String providerId);
}
