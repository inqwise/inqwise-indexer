package com.inqwise.indexer.load.adapters.local;

import com.inqwise.indexer.load.api.LoadProvider;
import com.inqwise.indexer.load.api.LoadProviderRegistry;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.Future;

public class InMemoryLoadProviderRegistry implements LoadProviderRegistry {
	private final Map<String, LoadProvider> providersById = new ConcurrentHashMap<>();

	public InMemoryLoadProviderRegistry register(String providerId, LoadProvider provider) {
		providersById.put(
			Objects.requireNonNull(providerId, "providerId"),
			Objects.requireNonNull(provider, "provider")
		);
		return this;
	}

	@Override
	public Future<LoadProvider> get(String providerId) {
		LoadProvider provider = providersById.get(providerId);
		if (provider == null) {
			return Future.failedFuture("Load provider not found: " + providerId);
		}
		return Future.succeededFuture(provider);
	}
}
