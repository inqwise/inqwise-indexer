package com.inqwise.indexer.service.invalidation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.lifecycle.TargetInvalidationEntries;
import com.inqwise.indexer.lifecycle.TargetInvalidationEntry;
import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class TargetInvalidationRegistryServices {
	public static final String DEFAULT_ADDRESS = "indexer.service.target-invalidation-registry";
	private static final String ADDRESS_PREFIX = DEFAULT_ADDRESS + ".";

	static final String OPERATION = "operation";
	static final String MARK_INVALIDATED = "mark_invalidated";
	static final String LIST_INVALIDATIONS = "list_invalidations";
	static final String TARGET_ID = "target_id";
	static final String MAX_TARGETS = "max_targets";
	static final String ENTRIES = "entries";
	static final String TRUNCATED = "truncated";
	static final String VERSION = "version";
	static final String EXPIRES_AT = "expires_at";

	private TargetInvalidationRegistryServices() {
	}

	public static TargetInvalidationRegistry proxy() {
		return proxy(Vertx.currentContext().owner());
	}

	public static TargetInvalidationRegistry proxy(Vertx vertx) {
		return proxy(vertx, DEFAULT_ADDRESS);
	}

	public static TargetInvalidationRegistry proxy(Vertx vertx, String address) {
		Objects.requireNonNull(vertx, "vertx");
		String serviceAddress = requireAddress(address);
		return new TargetInvalidationRegistry() {
			@Override
			public Future<Void> markInvalidated(Integer concreteTargetId) {
				Objects.requireNonNull(concreteTargetId, "concreteTargetId");
				return vertx.eventBus().<JsonObject>request(serviceAddress, new JsonObject()
					.put(OPERATION, MARK_INVALIDATED)
					.put(TARGET_ID, concreteTargetId))
					.mapEmpty();
			}

			@Override
			public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
				return vertx.eventBus().<JsonObject>request(serviceAddress, new JsonObject()
					.put(OPERATION, LIST_INVALIDATIONS)
					.put(MAX_TARGETS, maxTargets))
					.map(message -> fromJson(message.body()));
			}
		};
	}

	public static String address(String namespace) {
		Objects.requireNonNull(namespace, "namespace");
		String value = namespace.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("namespace must not be blank");
		}
		return ADDRESS_PREFIX + value;
	}

	static JsonObject toJson(TargetInvalidationEntries invalidations) {
		return new JsonObject()
			.put(ENTRIES, new JsonArray(invalidations.entries().stream()
				.map(entry -> new JsonObject()
					.put(TARGET_ID, entry.concreteTargetId())
					.put(VERSION, entry.version())
					.put(EXPIRES_AT, entry.expiresAt().toString()))
				.toList()))
			.put(TRUNCATED, invalidations.truncated());
	}

	private static TargetInvalidationEntries fromJson(JsonObject json) {
		List<TargetInvalidationEntry> entries = json.getJsonArray(ENTRIES, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(entry -> new TargetInvalidationEntry(
				entry.getInteger(TARGET_ID),
				entry.getLong(VERSION),
				Instant.parse(entry.getString(EXPIRES_AT))
			))
			.toList();
		return new TargetInvalidationEntries(entries, json.getBoolean(TRUNCATED, false));
	}

	static String requireAddress(String address) {
		Objects.requireNonNull(address, "address");
		String value = address.trim();
		if (value.isEmpty()) {
			throw new IllegalArgumentException("address must not be blank");
		}
		return value;
	}
}
