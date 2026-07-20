package com.inqwise.indexer.lifecycle;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.LongSupplier;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusConfig;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBusProvider;
import com.inqwise.indexer.lifecycle.IndexerLifecycleProviderSignal;

import io.vertx.core.Vertx;

public class VertxIndexerLifecycleEventBusProvider
	implements IndexerLifecycleEventBusProvider {
	private final Vertx vertx;
	private final long maxTransportLagMs;
	private final long signalCooldownMs;
	private final LongSupplier currentTimeMillis;
	private final ConcurrentMap<String, VertxIndexerLifecycleEventBus> busesByNamespace =
		new ConcurrentHashMap<>();

	public VertxIndexerLifecycleEventBusProvider(Vertx vertx) {
		this(vertx, VertxIndexerLifecycleEventBusOptions.builder().build());
	}

	public VertxIndexerLifecycleEventBusProvider(
		Vertx vertx,
		VertxIndexerLifecycleEventBusOptions options
	) {
		this(vertx, options, System::currentTimeMillis);
	}

	VertxIndexerLifecycleEventBusProvider(
		Vertx vertx,
		VertxIndexerLifecycleEventBusOptions options,
		LongSupplier currentTimeMillis
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		VertxIndexerLifecycleEventBusOptions validated = Objects.requireNonNull(
			options,
			"options"
		).validate();
		this.maxTransportLagMs = validated.getMaxTransportLagMs();
		this.signalCooldownMs = validated.getSignalCooldownMs();
		this.currentTimeMillis = Objects.requireNonNull(
			currentTimeMillis,
			"currentTimeMillis"
		);
	}

	@Override
	public IndexerLifecycleEventBus create(IndexerLifecycleEventBusConfig config) {
		Objects.requireNonNull(config, "config");
		return busesByNamespace.computeIfAbsent(
			config.namespace(),
			namespace -> new VertxIndexerLifecycleEventBus(
				vertx.eventBus(),
				namespace,
				maxTransportLagMs,
				signalCooldownMs,
				currentTimeMillis
			)
		);
	}

	public void reportProviderSignal(
		IndexerLifecycleEventBusConfig config,
		IndexerLifecycleProviderSignal signal
	) {
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(signal, "signal");
		VertxIndexerLifecycleEventBus bus = busesByNamespace.get(config.namespace());
		if (bus != null) {
			vertx.runOnContext(ignored -> bus.reportSignal(signal));
		}
	}
}
