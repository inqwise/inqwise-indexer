package com.inqwise.indexer;

import java.util.Objects;
import java.util.ServiceLoader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.spi.IndexerActionProvider;

public class Actions {
	private static final Logger logger = LogManager.getLogger(Actions.class);

	private static ServiceLoader<IndexerActionProvider> providers;

	private synchronized static void load() {
		if (providers == null || !providers.iterator().hasNext()) {
			logger.debug("load");
			providers = ServiceLoader.load(IndexerActionProvider.class);
			logger.debug("found {} provider(s)", providers.stream().count());
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
