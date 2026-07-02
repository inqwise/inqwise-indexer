package com.inqwise.indexer;

import java.time.Clock;
import java.time.Duration;

import com.inqwise.indexer.hot.InMemoryTargetInvalidationRegistry;

public final class TestMetadataChangeNotifiers {
	private TestMetadataChangeNotifiers() {
	}

	public static MetadataChangeNotifier create(IndexerLifecycleEventBus eventBus) {
		return new MetadataChangeNotifier(
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC()),
			eventBus
		);
	}
}
