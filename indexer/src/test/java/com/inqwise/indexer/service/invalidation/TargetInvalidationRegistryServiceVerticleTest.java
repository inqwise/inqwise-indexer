package com.inqwise.indexer.service.invalidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetInvalidationRegistryServiceVerticleTest {
	@Test
	void derivesNamespaceIsolatedAddresses() {
		assertEquals(
			"indexer.service.target-invalidation-registry.production",
			TargetInvalidationRegistryServices.address(" production ")
		);
	}

	@Test
	void marksAndListsInvalidationsThroughProxy(Vertx vertx, VertxTestContext testContext) {
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());
		TargetInvalidationRegistry proxy = TargetInvalidationRegistryServices.proxy(vertx);

		vertx.deployVerticle(new TargetInvalidationRegistryServiceVerticle(registry))
			.compose(ignored -> proxy.markInvalidated(10))
			.compose(ignored -> proxy.markInvalidated(10))
			.compose(ignored -> proxy.listInvalidations(10))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertFalse(result.truncated());
				assertEquals(1, result.entries().size());
				assertEquals(10, result.entries().get(0).concreteTargetId());
				assertEquals(2L, result.entries().get(0).version());
				testContext.completeNow();
			})));
	}

	@Test
	void propagatesRegistryFailuresThroughProxy(Vertx vertx, VertxTestContext testContext) {
		TargetInvalidationRegistry proxy = TargetInvalidationRegistryServices.proxy(
			vertx,
			"test.target-invalidation.failure"
		);
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());

		vertx.deployVerticle(new TargetInvalidationRegistryServiceVerticle(
			registry,
			"test.target-invalidation.failure"
		)).compose(ignored -> proxy.listInvalidations(0))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(error.getMessage().contains("maxTargets must be positive"));
				testContext.completeNow();
			})));
	}

	@Test
	void convertsMalformedMessagesToFailedReplies(Vertx vertx, VertxTestContext testContext) {
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());

		vertx.deployVerticle(new TargetInvalidationRegistryServiceVerticle(registry))
			.compose(ignored -> vertx.eventBus().request(
				TargetInvalidationRegistryServices.DEFAULT_ADDRESS,
				new JsonObject()
					.put(TargetInvalidationRegistryServices.OPERATION,
						TargetInvalidationRegistryServices.LIST_INVALIDATIONS)
					.put(TargetInvalidationRegistryServices.MAX_TARGETS, "invalid")
			))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertInstanceOf(ReplyException.class, error);
				testContext.completeNow();
			})));
	}
}
