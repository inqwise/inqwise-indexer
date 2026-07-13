package com.inqwise.indexer.load.testing;


import java.time.Clock;
import java.time.Duration;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;

public final class LoadTestMetadataChangeNotifiers {
	private LoadTestMetadataChangeNotifiers() {
	}

	public static MetadataChangeNotifier create(IndexerLifecycleEventBus eventBus) {
		return new MetadataChangeNotifier(
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC()),
			eventBus
		);
	}
}
