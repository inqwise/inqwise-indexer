package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class InMemoryTargetInvalidationRegistryTest {
	@Test
	void marksTargetInvalidationAndIncrementsVersion() {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), clock);

		registry.markInvalidated(10);
		clock.advance(Duration.ofMinutes(1));
		registry.markInvalidated(10);

		TargetInvalidationEntries entries = registry.listInvalidations(10);
		assertEquals(false, entries.truncated());
		assertEquals(1, entries.entries().size());
		assertEquals(10, entries.entries().get(0).concreteTargetId());
		assertEquals(2L, entries.entries().get(0).version());
		assertEquals(Instant.parse("2026-05-31T08:06:00Z"), entries.entries().get(0).expiresAt());
	}

	@Test
	void expiresTargetInvalidation() {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), clock);

		registry.markInvalidated(10);
		clock.advance(Duration.ofMinutes(5));

		assertTrue(registry.listInvalidations(10).entries().isEmpty());
	}

	@Test
	void reportsTruncationWhenEntriesExceedMaxTargets() {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), clock);

		registry.markInvalidated(10);
		registry.markInvalidated(11);
		registry.markInvalidated(12);

		TargetInvalidationEntries entries = registry.listInvalidations(2);
		assertEquals(true, entries.truncated());
		assertEquals(2, entries.entries().size());
		assertEquals(10, entries.entries().get(0).concreteTargetId());
		assertEquals(11, entries.entries().get(1).concreteTargetId());
	}

	@Test
	void optionsDeriveTtlFromPollIntervalAndRetentionFactor() {
		TargetInvalidationRegistryOptions options =
			new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 3);

		assertEquals(Duration.ofSeconds(90), options.ttl());
	}

	@Test
	void rejectsInvalidOptions() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new TargetInvalidationRegistryOptions(Duration.ZERO, 3)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new TargetInvalidationRegistryOptions(Duration.ofSeconds(30), 1)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new InMemoryTargetInvalidationRegistry(Duration.ZERO, Clock.systemUTC())
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> new InMemoryTargetInvalidationRegistry(Duration.ofSeconds(30), Clock.systemUTC())
				.listInvalidations(0)
		);
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
