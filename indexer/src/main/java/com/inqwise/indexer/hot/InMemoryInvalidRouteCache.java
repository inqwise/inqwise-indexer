package com.inqwise.indexer.hot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryInvalidRouteCache implements InvalidRouteCache {
	private final Duration ttl;
	private final Clock clock;
	private final ConcurrentMap<InvalidRouteSignature, InvalidRouteRecord> records =
		new ConcurrentHashMap<>();

	public InMemoryInvalidRouteCache(Duration ttl) {
		this(ttl, Clock.systemUTC());
	}

	public InMemoryInvalidRouteCache(Duration ttl, Clock clock) {
		if (Objects.requireNonNull(ttl, "ttl").isNegative() || ttl.isZero()) {
			throw new IllegalArgumentException("ttl must be positive");
		}

		this.ttl = ttl;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Optional<InvalidRouteRecord> find(InvalidRouteSignature signature) {
		InvalidRouteRecord record = records.get(signature);
		if (record == null) {
			return Optional.empty();
		}

		if (isExpired(record)) {
			records.remove(signature, record);
			return Optional.empty();
		}

		return Optional.of(record);
	}

	@Override
	public void record(InvalidRouteSignature signature, String reason) {
		Instant now = clock.instant();
		records.compute(signature, (ignored, existing) -> {
			if (existing == null || isExpired(existing, now)) {
				return new InvalidRouteRecord(
					signature,
					reason,
					now,
					now,
					now.plus(ttl),
					1L
				);
			}

			return new InvalidRouteRecord(
				signature,
				reason,
				existing.firstSeenAt(),
				now,
				now.plus(ttl),
				existing.count() + 1
			);
		});
	}

	@Override
	public void invalidateMatching(InvalidRouteInvalidation invalidation) {
		Iterator<Map.Entry<InvalidRouteSignature, InvalidRouteRecord>> iterator =
			records.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<InvalidRouteSignature, InvalidRouteRecord> entry = iterator.next();
			if (invalidation.matches(entry.getKey())) {
				iterator.remove();
			}
		}
	}

	private boolean isExpired(InvalidRouteRecord record) {
		return isExpired(record, clock.instant());
	}

	private boolean isExpired(InvalidRouteRecord record, Instant now) {
		return !record.expiresAt().isAfter(now);
	}
}
