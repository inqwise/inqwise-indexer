package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryTargetInvalidationRegistryProviderTest {
	@Test
	void rejectsConflictingOptionsForOneNamespace() {
		InMemoryTargetInvalidationRegistryProvider provider =
			new InMemoryTargetInvalidationRegistryProvider();
		provider.create(new TargetInvalidationRegistryConfig(
			"production",
			new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 3, 100)
		));

		assertThrows(
			IllegalArgumentException.class,
			() -> provider.create(new TargetInvalidationRegistryConfig(
				"production",
				new TargetInvalidationRegistryOptions(Duration.ofSeconds(10), 3, 100)
			))
		);
	}

	@Test
	void sharesOneLogicalRegistryPerNamespaceAndIsolatesNamespaces(
		VertxTestContext testContext
	) {
		InMemoryTargetInvalidationRegistryProvider provider =
			new InMemoryTargetInvalidationRegistryProvider();
		TargetInvalidationRegistryOptions options =
			new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 3, 100);
		TargetInvalidationRegistry writer = provider.create(
			new TargetInvalidationRegistryConfig("production", options)
		);
		TargetInvalidationRegistry reader = provider.create(
			new TargetInvalidationRegistryConfig("production", options)
		);
		TargetInvalidationRegistry isolated = provider.create(
			new TargetInvalidationRegistryConfig("staging", options)
		);

		writer.markInvalidated(17)
			.compose(ignored -> reader.listInvalidations(100))
			.compose(shared -> {
				testContext.verify(() -> {
					assertEquals(1, shared.entries().size());
					assertEquals(17, shared.entries().get(0).concreteTargetId());
				});
				return isolated.listInvalidations(100);
			})
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertTrue(result.entries().isEmpty());
				testContext.completeNow();
			})));
	}
}
