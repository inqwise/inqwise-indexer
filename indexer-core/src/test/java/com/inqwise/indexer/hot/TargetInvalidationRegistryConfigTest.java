package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class TargetInvalidationRegistryConfigTest {
	private final TargetInvalidationRegistryOptions options =
		new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 3, 100);

	@Test
	void normalizesNamespace() {
		TargetInvalidationRegistryConfig config =
			new TargetInvalidationRegistryConfig(" production ", options);

		assertEquals("production", config.namespace());
		assertEquals(options, config.options());
	}

	@Test
	void rejectsBlankNamespace() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new TargetInvalidationRegistryConfig("  ", options)
		);
	}
}
