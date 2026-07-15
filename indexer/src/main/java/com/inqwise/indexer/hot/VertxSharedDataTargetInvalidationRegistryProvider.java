package com.inqwise.indexer.hot;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.inqwise.indexer.lifecycle.TargetInvalidationEntries;
import com.inqwise.indexer.lifecycle.TargetInvalidationEntry;
import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.SharedData;

public class VertxSharedDataTargetInvalidationRegistryProvider
	implements TargetInvalidationRegistryProvider {
	private static final String MAP_PREFIX = "inqwise.target-invalidations.";
	private static final String LOCK_PREFIX = "inqwise.target-invalidation-lock.";

	private final SharedData sharedData;
	private final Clock clock;
	private final ConcurrentMap<String, RegistryEntry> entriesByNamespace =
		new ConcurrentHashMap<>();

	public VertxSharedDataTargetInvalidationRegistryProvider(Vertx vertx) {
		this(Objects.requireNonNull(vertx, "vertx").sharedData(), Clock.systemUTC());
	}

	VertxSharedDataTargetInvalidationRegistryProvider(SharedData sharedData, Clock clock) {
		this.sharedData = Objects.requireNonNull(sharedData, "sharedData");
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	@Override
	public TargetInvalidationRegistry create(TargetInvalidationRegistryConfig config) {
		Objects.requireNonNull(config, "config");
		return entriesByNamespace.compute(config.namespace(), (ignored, existing) -> {
			if (existing != null) {
				if (!existing.options().equals(config.options())) {
					throw new IllegalArgumentException(
						"Conflicting target invalidation options for namespace: "
							+ config.namespace()
					);
				}
				return existing;
			}

			Future<AsyncMap<String, JsonObject>> map = sharedData.getAsyncMap(
				MAP_PREFIX + config.namespace()
			);
			return new RegistryEntry(
				config.options(),
				new VertxSharedDataTargetInvalidationRegistry(
					sharedData,
					map,
					LOCK_PREFIX + config.namespace() + ".",
					config.options(),
					clock
				)
			);
		}).registry();
	}

	private record RegistryEntry(
		TargetInvalidationRegistryOptions options,
		TargetInvalidationRegistry registry
	) {
	}

	private static class VertxSharedDataTargetInvalidationRegistry
		implements TargetInvalidationRegistry {
		private static final String VERSION = "version";
		private static final String EXPIRES_AT = "expires_at";

		private final SharedData sharedData;
		private final Future<AsyncMap<String, JsonObject>> map;
		private final String lockPrefix;
		private final long ttlMs;
		private final Clock clock;

		private VertxSharedDataTargetInvalidationRegistry(
			SharedData sharedData,
			Future<AsyncMap<String, JsonObject>> map,
			String lockPrefix,
			TargetInvalidationRegistryOptions options,
			Clock clock
		) {
			this.sharedData = sharedData;
			this.map = map;
			this.lockPrefix = lockPrefix;
			this.ttlMs = options.ttl().toMillis();
			this.clock = clock;
		}

		@Override
		public Future<Void> markInvalidated(Integer concreteTargetId) {
			Objects.requireNonNull(concreteTargetId, "concreteTargetId");
			String key = concreteTargetId.toString();
			return sharedData.withLock(lockPrefix + key, () -> map.compose(entries ->
				entries.get(key).compose(existing -> {
					Instant now = clock.instant();
					long version = version(existing, now) + 1L;
					JsonObject updated = new JsonObject()
						.put(VERSION, version)
						.put(EXPIRES_AT, now.plusMillis(ttlMs).toString());
					return entries.put(key, updated, ttlMs);
				})
			));
		}

		@Override
		public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
			if (maxTargets <= 0) {
				return Future.failedFuture("maxTargets must be positive");
			}

			return map.compose(entries -> entries.size().compose(size -> {
				if (size > maxTargets) {
					return Future.succeededFuture(new TargetInvalidationEntries(List.of(), true));
				}

				return entries.entries().map(values -> toEntries(values, maxTargets));
			}));
		}

		private TargetInvalidationEntries toEntries(
			Map<String, JsonObject> values,
			int maxTargets
		) {
			Instant now = clock.instant();
			List<TargetInvalidationEntry> entries = values.entrySet().stream()
				.map(entry -> toEntry(entry, now))
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(TargetInvalidationEntry::concreteTargetId))
				.limit(maxTargets)
				.toList();
			return new TargetInvalidationEntries(entries, values.size() > maxTargets);
		}

		private TargetInvalidationEntry toEntry(
			Map.Entry<String, JsonObject> entry,
			Instant now
		) {
			Instant expiresAt = Instant.parse(entry.getValue().getString(EXPIRES_AT));
			if (!expiresAt.isAfter(now)) {
				return null;
			}

			return new TargetInvalidationEntry(
				Integer.valueOf(entry.getKey()),
				entry.getValue().getLong(VERSION),
				expiresAt
			);
		}

		private long version(JsonObject existing, Instant now) {
			if (existing == null) {
				return 0L;
			}

			Instant expiresAt = Instant.parse(existing.getString(EXPIRES_AT));
			return expiresAt.isAfter(now) ? existing.getLong(VERSION) : 0L;
		}
	}
}
