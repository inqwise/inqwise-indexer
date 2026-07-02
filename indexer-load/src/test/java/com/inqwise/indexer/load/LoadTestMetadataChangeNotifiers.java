package com.inqwise.indexer.load;

import java.time.Clock;
import java.time.Duration;

import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.hot.InMemoryTargetInvalidationRegistry;

final class LoadTestMetadataChangeNotifiers {
	private LoadTestMetadataChangeNotifiers() {
	}

	static MetadataChangeNotifier create(IndexerLifecycleEventBus eventBus) {
		return new MetadataChangeNotifier(
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC()),
			eventBus
		);
	}
}
