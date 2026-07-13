package com.inqwise.indexer.hot;

import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class VertxSharedDataTargetInvalidationRegistryProviderTest {
	private static final TargetInvalidationRegistryOptions OPTIONS =
		new TargetInvalidationRegistryOptions(Duration.ofSeconds(5), 2, 2);

	@Test
	void sharesRegistryByNamespaceAndRejectsConflictingOptions(Vertx vertx) {
		VertxSharedDataTargetInvalidationRegistryProvider provider =
			new VertxSharedDataTargetInvalidationRegistryProvider(vertx);

		TargetInvalidationRegistry first = provider.create(config("production"));
		TargetInvalidationRegistry second = provider.create(config("production"));
		TargetInvalidationRegistry isolated = provider.create(config("review"));

		assertSame(first, second);
		assertNotSame(first, isolated);
		assertThrows(IllegalArgumentException.class, () -> provider.create(
			new TargetInvalidationRegistryConfig(
				"production",
				new TargetInvalidationRegistryOptions(Duration.ofSeconds(1), 2, 2)
			)
		));
	}

	@Test
	void atomicallyAdvancesVersionsAcrossProviderInstances(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TargetInvalidationRegistry first = registry(vertx, "concurrent");
		TargetInvalidationRegistry second = registry(vertx, "concurrent");

		Future.all(first.markInvalidated(10), second.markInvalidated(10))
			.compose(ignored -> first.listInvalidations(2))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(1, result.entries().size());
				assertEquals(2L, result.entries().get(0).version());
				testContext.completeNow();
			})));
	}

	@Test
	void reportsTruncationWithoutReturningAnOversizedResult(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TargetInvalidationRegistry registry = registry(vertx, "truncated");

		Future.all(
			registry.markInvalidated(10),
			registry.markInvalidated(20),
			registry.markInvalidated(30)
		).compose(ignored -> registry.listInvalidations(2))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertTrue(result.truncated());
				assertTrue(result.entries().size() <= 2);
				testContext.completeNow();
			})));
	}

	private TargetInvalidationRegistry registry(Vertx vertx, String namespace) {
		return new VertxSharedDataTargetInvalidationRegistryProvider(vertx).create(config(namespace));
	}

	private TargetInvalidationRegistryConfig config(String namespace) {
		return new TargetInvalidationRegistryConfig(namespace, OPTIONS);
	}
}
