package com.inqwise.indexer.adapters.local;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.inqwise.indexer.hot.TargetInvalidationEntries;
import com.inqwise.indexer.hot.TargetInvalidationEntry;
import com.inqwise.indexer.hot.TargetInvalidationRegistry;
import com.inqwise.indexer.hot.TargetInvalidationRegistryOptions;

import io.vertx.core.Future;

public class InMemoryTargetInvalidationRegistry implements TargetInvalidationRegistry {
	private final Duration ttl;
	private final Clock clock;
	private final ConcurrentMap<Integer, TargetInvalidationEntry> entriesByTargetId =
		new ConcurrentHashMap<>();

	public InMemoryTargetInvalidationRegistry(TargetInvalidationRegistryOptions options) {
		this(options.ttl(), Clock.systemUTC());
	}

	public InMemoryTargetInvalidationRegistry(Duration ttl, Clock clock) {
		if (Objects.requireNonNull(ttl, "ttl").isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("ttl must be positive");
		}

		this.ttl = ttl;
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public Future<Void> markInvalidated(Integer concreteTargetId) {
		Objects.requireNonNull(concreteTargetId, "concreteTargetId");
		Instant now = clock.instant();
		entriesByTargetId.compute(concreteTargetId, (ignored, existing) -> {
			long version = existing == null || isExpired(existing, now)
				? 1L
				: existing.version() + 1;
			return new TargetInvalidationEntry(
				concreteTargetId,
				version,
				now.plus(ttl)
			);
		});
		return Future.succeededFuture();
	}

	@Override
	public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
		if (maxTargets <= 0) {
			return Future.failedFuture("maxTargets must be positive");
		}

		removeExpired();
		List<TargetInvalidationEntry> entries = entriesByTargetId.entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.map(Map.Entry::getValue)
			.limit(maxTargets)
			.sorted(Comparator.comparing(TargetInvalidationEntry::concreteTargetId))
			.toList();

		return Future.succeededFuture(new TargetInvalidationEntries(
			entries,
			entriesByTargetId.size() > maxTargets
		));
	}

	private void removeExpired() {
		Instant now = clock.instant();
		entriesByTargetId.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
	}

	private boolean isExpired(TargetInvalidationEntry entry, Instant now) {
		return !entry.expiresAt().isAfter(now);
	}
}
