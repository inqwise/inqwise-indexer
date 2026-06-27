package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IndexerLifecycleEventBusConfigTest {
	@Test
	void normalizesNamespace() {
		IndexerLifecycleEventBusConfig config =
			new IndexerLifecycleEventBusConfig(" production ");

		assertEquals("production", config.namespace());
	}

	@Test
	void rejectsBlankNamespace() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new IndexerLifecycleEventBusConfig("  ")
		);
	}
}
