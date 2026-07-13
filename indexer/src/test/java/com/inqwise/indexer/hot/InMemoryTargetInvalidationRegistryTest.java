package com.inqwise.indexer.hot;

import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryTargetInvalidationRegistryTest {
	@Test
	void marksTargetInvalidationAndIncrementsVersion(VertxTestContext testContext) {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), clock);

		registry.markInvalidated(10)
			.compose(ignored -> {
				clock.advance(Duration.ofMinutes(1));
				return registry.markInvalidated(10);
			})
			.compose(ignored -> registry.listInvalidations(10))
			.onComplete(testContext.succeeding(entries -> testContext.verify(() -> {
				assertEquals(false, entries.truncated());
				assertEquals(1, entries.entries().size());
				assertEquals(10, entries.entries().get(0).concreteTargetId());
				assertEquals(2L, entries.entries().get(0).version());
				assertEquals(
					Instant.parse("2026-05-31T08:06:00Z"),
					entries.entries().get(0).expiresAt()
				);
				testContext.completeNow();
			})));
	}

	@Test
	void expiresTargetInvalidation(VertxTestContext testContext) {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), clock);

		registry.markInvalidated(10)
			.compose(ignored -> {
				clock.advance(Duration.ofMinutes(5));
				return registry.listInvalidations(10);
			})
			.onComplete(testContext.succeeding(entries -> testContext.verify(() -> {
				assertTrue(entries.entries().isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void reportsTruncationWhenEntriesExceedMaxTargets(VertxTestContext testContext) {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), clock);

		registry.markInvalidated(10)
			.compose(ignored -> registry.markInvalidated(11))
			.compose(ignored -> registry.markInvalidated(12))
			.compose(ignored -> registry.listInvalidations(2))
			.onComplete(testContext.succeeding(entries -> testContext.verify(() -> {
				assertEquals(true, entries.truncated());
				assertEquals(2, entries.entries().size());
				assertEquals(10, entries.entries().get(0).concreteTargetId());
				assertEquals(11, entries.entries().get(1).concreteTargetId());
				testContext.completeNow();
			})));
	}

	@Test
	void optionsDeriveTtlFromPollIntervalAndRetentionFactor() {
		TargetInvalidationRegistryOptions options =
			new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 3, 100);

		assertEquals(Duration.ofSeconds(90), options.ttl());
	}

	@Test
	void rejectsInvalidOptions() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new TargetInvalidationRegistryOptions(Duration.ZERO, 3, 100)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 1, 100)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 3, 0)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new InMemoryTargetInvalidationRegistry(Duration.ZERO, Clock.systemUTC())
		);
	}

	@Test
	void rejectsInvalidListLimit(VertxTestContext testContext) {
		new InMemoryTargetInvalidationRegistry(Duration.ofSeconds(30), Clock.systemUTC())
			.listInvalidations(0)
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("maxTargets must be positive", error.getMessage());
				testContext.completeNow();
			})));
	}

	private static class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
