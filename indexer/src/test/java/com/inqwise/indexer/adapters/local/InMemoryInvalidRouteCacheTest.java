package com.inqwise.indexer.adapters.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.routing.InvalidRouteInvalidation;
import com.inqwise.indexer.routing.InvalidRouteRecord;
import com.inqwise.indexer.routing.InvalidRouteSignature;

import com.inqwise.indexer.actions.IndexerActionType;

class InMemoryInvalidRouteCacheTest {
	@Test
	void recordsAndRefreshesInvalidRouteSignature() {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryInvalidRouteCache cache = new InMemoryInvalidRouteCache(Duration.ofMinutes(5), clock);
		InvalidRouteSignature signature = signature("customers", "2026-05");

		cache.record(signature, "missing target");
		clock.advance(Duration.ofMinutes(1));
		cache.record(signature, "still missing");

		InvalidRouteRecord record = cache.find(signature).orElseThrow();
		assertEquals("still missing", record.reason());
		assertEquals(2L, record.count());
		assertEquals(Instant.parse("2026-05-31T08:00:00Z"), record.firstSeenAt());
		assertEquals(Instant.parse("2026-05-31T08:01:00Z"), record.lastSeenAt());
		assertEquals(Instant.parse("2026-05-31T08:06:00Z"), record.expiresAt());
	}

	@Test
	void expiresInvalidRouteSignature() {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryInvalidRouteCache cache = new InMemoryInvalidRouteCache(Duration.ofMinutes(5), clock);
		InvalidRouteSignature signature = signature("customers", "2026-05");

		cache.record(signature, "missing target");
		clock.advance(Duration.ofMinutes(5));

		assertTrue(cache.find(signature).isEmpty());
	}

	@Test
	void invalidatesMatchingRouteSignatures() {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryInvalidRouteCache cache = new InMemoryInvalidRouteCache(Duration.ofMinutes(5), clock);
		InvalidRouteSignature customers = signature("customers", "2026-05");
		InvalidRouteSignature orders = signature("orders", "2026-05");

		cache.record(customers, "missing target");
		cache.record(orders, "missing target");
		cache.invalidateMatching(new InvalidRouteInvalidation(
			"customers",
			null,
			null,
			null,
			null
		));

		assertTrue(cache.find(customers).isEmpty());
		assertTrue(cache.find(orders).isPresent());
	}

	@Test
	void exactPeriodInvalidationDoesNotMatchOtherPeriods() {
		MutableClock clock = new MutableClock(Instant.parse("2026-05-31T08:00:00Z"));
		InMemoryInvalidRouteCache cache = new InMemoryInvalidRouteCache(Duration.ofMinutes(5), clock);
		InvalidRouteSignature may = signature("customers", "2026-05");
		InvalidRouteSignature june = signature("customers", "2026-06");
		InvalidRouteSignature broad = signature("customers", null);

		cache.record(may, "missing target");
		cache.record(june, "missing target");
		cache.record(broad, "missing target");
		cache.invalidateMatching(InvalidRouteInvalidation.exactPeriodKey(
			"customers",
			"2026-05",
			null,
			null,
			null
		));

		assertTrue(cache.find(may).isEmpty());
		assertTrue(cache.find(june).isPresent());
		assertTrue(cache.find(broad).isPresent());
	}

	@Test
	void rejectsNonPositiveTtl() {
		assertThrows(
			IllegalArgumentException.class,
			() -> new InMemoryInvalidRouteCache(Duration.ZERO)
		);
	}

	private InvalidRouteSignature signature(String targetName, String periodKey) {
		return new InvalidRouteSignature(
			targetName,
			periodKey,
			null,
			null,
			null,
			IndexerActionType.PUT_DOCUMENT
		);
	}

	private static class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
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
