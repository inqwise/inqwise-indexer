package com.inqwise.indexer.testing;

import java.time.Clock;
import java.time.Duration;

import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;

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
