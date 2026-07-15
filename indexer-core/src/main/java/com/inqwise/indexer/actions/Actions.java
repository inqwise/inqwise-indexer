package com.inqwise.indexer.actions;

import java.util.Objects;
import java.util.ServiceLoader;

public class Actions {
	private static ServiceLoader<IndexerActionProvider> providers;

	private synchronized static void load() {
		if (providers == null || !providers.iterator().hasNext()) {
			providers = ServiceLoader.load(IndexerActionProvider.class);
		}
	}

	public synchronized static IndexerActionProvider getProvider(IndexerActionType type) {
		Objects.requireNonNull(type, "type");
		load();
		for (var next : providers) {
			if (next.type() == type) {
				return next;
			}
		}

		throw new IllegalArgumentException("Not found IndexerActionProvider with type: '" + type + "'");
	}
}
