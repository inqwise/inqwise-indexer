package com.inqwise.indexer.providers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

import com.inqwise.indexer.catalog.indexers.IndexerType;

import io.vertx.core.Future;

public final class IndexerProviders {
	private final List<IndexerProvider> providers;

	public IndexerProviders(List<IndexerProvider> providers) {
		this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
	}

	public static IndexerProviders load() {
		return new IndexerProviders(ServiceLoader.load(IndexerProvider.class)
			.stream()
			.map(ServiceLoader.Provider::get)
			.toList());
	}

	public Future<Optional<ResolvedIndexer>> getIndexerById(Integer indexerId) {
		Future<Optional<ResolvedIndexer>> resolved = Future.succeededFuture(Optional.empty());

		for (IndexerProvider provider : providers) {
			resolved = resolved.compose(current -> {
				if (current.isPresent()) {
					return Future.succeededFuture(current);
				}

				return provider.getIndexerById(indexerId);
			});
		}

		return resolved;
	}

	public Future<List<ResolvedIndexer>> listIndexers(IndexerProviderQuery query) {
		List<IndexerProvider> matchingProviders = providersFor(query);
		List<Future<List<ResolvedIndexer>>> futures = matchingProviders.stream()
			.map(provider -> provider.listIndexers(query))
			.toList();

		return Future.join(futures)
			.map(ignored -> {
				List<ResolvedIndexer> indexers = new ArrayList<>();
				for (Future<List<ResolvedIndexer>> future : futures) {
					indexers.addAll(future.result());
				}

				return indexers;
			});
	}

	private List<IndexerProvider> providersFor(IndexerProviderQuery query) {
		List<IndexerType> types = query == null ? List.of() : query.types();
		if (types.isEmpty()) {
			return providers;
		}

		return providers.stream()
			.filter(provider -> types.contains(provider.type()))
			.toList();
	}
}
