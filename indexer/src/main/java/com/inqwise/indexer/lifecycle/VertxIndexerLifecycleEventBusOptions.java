package com.inqwise.indexer.lifecycle;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class VertxIndexerLifecycleEventBusOptions {
	public static final long DEFAULT_MAX_TRANSPORT_LAG_MS = 30_000L;
	public static final long DEFAULT_SIGNAL_COOLDOWN_MS = 60_000L;

	public static final class Keys {
		public static final String MAX_TRANSPORT_LAG_MS = "max_transport_lag_ms";
		public static final String SIGNAL_COOLDOWN_MS = "signal_cooldown_ms";

		private Keys() {
		}
	}

	private long maxTransportLagMs = DEFAULT_MAX_TRANSPORT_LAG_MS;
	private long signalCooldownMs = DEFAULT_SIGNAL_COOLDOWN_MS;

	public VertxIndexerLifecycleEventBusOptions() {
	}

	public VertxIndexerLifecycleEventBusOptions(JsonObject json) {
		maxTransportLagMs = json.getLong(
			Keys.MAX_TRANSPORT_LAG_MS,
			DEFAULT_MAX_TRANSPORT_LAG_MS
		);
		signalCooldownMs = json.getLong(
			Keys.SIGNAL_COOLDOWN_MS,
			DEFAULT_SIGNAL_COOLDOWN_MS
		);
		validate();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.MAX_TRANSPORT_LAG_MS, maxTransportLagMs)
			.put(Keys.SIGNAL_COOLDOWN_MS, signalCooldownMs);
	}

	public long getMaxTransportLagMs() {
		return maxTransportLagMs;
	}

	public VertxIndexerLifecycleEventBusOptions setMaxTransportLagMs(
		long maxTransportLagMs
	) {
		this.maxTransportLagMs = maxTransportLagMs;
		return this;
	}

	public long getSignalCooldownMs() {
		return signalCooldownMs;
	}

	public VertxIndexerLifecycleEventBusOptions setSignalCooldownMs(
		long signalCooldownMs
	) {
		this.signalCooldownMs = signalCooldownMs;
		return this;
	}

	public VertxIndexerLifecycleEventBusOptions validate() {
		if (maxTransportLagMs <= 0L) {
			throw new IllegalArgumentException("maxTransportLagMs must be greater than zero");
		}
		if (signalCooldownMs <= 0L) {
			throw new IllegalArgumentException("signalCooldownMs must be greater than zero");
		}
		return this;
	}
}
