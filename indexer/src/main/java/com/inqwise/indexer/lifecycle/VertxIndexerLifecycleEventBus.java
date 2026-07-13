package com.inqwise.indexer.lifecycle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerLifecycleProviderSignal;
import com.inqwise.indexer.lifecycle.IndexerLifecycleSubscription;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

final class VertxIndexerLifecycleEventBus implements IndexerLifecycleEventBus {
	private static final String ADDRESS_PREFIX = "inqwise.indexer.lifecycle.";
	private static final String INDEXER_SUFFIX = ".indexer";
	private static final String TARGET_SUFFIX = ".target";
	private static final String PUBLISHED_AT = "published_at_epoch_ms";
	private static final String PAYLOAD = "payload";

	private final EventBus eventBus;
	private final String indexerAddress;
	private final String targetAddress;
	private final long maxTransportLagMs;
	private final long signalCooldownMs;
	private final LongSupplier currentTimeMillis;
	private final List<Handler<IndexerLifecycleProviderSignal>> signalSubscribers =
		new ArrayList<>();
	private final Map<IndexerLifecycleProviderSignal, Long> lastSignalAt =
		new EnumMap<>(IndexerLifecycleProviderSignal.class);

	VertxIndexerLifecycleEventBus(
		EventBus eventBus,
		String namespace,
		long maxTransportLagMs,
		long signalCooldownMs,
		LongSupplier currentTimeMillis
	) {
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.indexerAddress = indexerAddress(namespace);
		this.targetAddress = targetAddress(namespace);
		this.maxTransportLagMs = maxTransportLagMs;
		this.signalCooldownMs = signalCooldownMs;
		this.currentTimeMillis = Objects.requireNonNull(
			currentTimeMillis,
			"currentTimeMillis"
		);
	}

	@Override
	public Future<Void> publish(IndexerMetadataChanged event) {
		Objects.requireNonNull(event, "event");
		eventBus.publish(indexerAddress, envelope(event.toJson()));
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> publish(TargetMetadataChanged event) {
		Objects.requireNonNull(event, "event");
		eventBus.publish(targetAddress, envelope(event.toJson()));
		return Future.succeededFuture();
	}

	@Override
	public Future<IndexerLifecycleSubscription> subscribe(
		Handler<IndexerMetadataChanged> handler
	) {
		return subscribe(indexerAddress, IndexerMetadataChanged::new, handler);
	}

	@Override
	public Future<IndexerLifecycleSubscription> subscribeTarget(
		Handler<TargetMetadataChanged> handler
	) {
		return subscribe(targetAddress, TargetMetadataChanged::new, handler);
	}

	@Override
	public synchronized Future<IndexerLifecycleSubscription> subscribeProviderSignals(
		Handler<IndexerLifecycleProviderSignal> handler
	) {
		Objects.requireNonNull(handler, "handler");
		signalSubscribers.add(handler);
		return Future.succeededFuture(() -> {
			synchronized (this) {
				signalSubscribers.remove(handler);
			}
			return Future.succeededFuture();
		});
	}

	private <T> Future<IndexerLifecycleSubscription> subscribe(
		String address,
		Function<JsonObject, T> decoder,
		Handler<T> handler
	) {
		Objects.requireNonNull(handler, "handler");
		MessageConsumer<JsonObject> consumer = eventBus.consumer(
			address,
			message -> consume(message.body(), decoder, handler)
		);
		consumer.exceptionHandler(error -> emitSignal(
			IndexerLifecycleProviderSignal.DELIVERY_LOST
		));
		return consumer.completion().map(ignored -> consumer::unregister);
	}

	private <T> void consume(
		JsonObject envelope,
		Function<JsonObject, T> decoder,
		Handler<T> handler
	) {
		T event;
		long publishedAt;
		try {
			Objects.requireNonNull(envelope, "envelope");
			publishedAt = Objects.requireNonNull(
				envelope.getLong(PUBLISHED_AT),
				PUBLISHED_AT
			);
			JsonObject payload = Objects.requireNonNull(
				envelope.getJsonObject(PAYLOAD),
				PAYLOAD
			);
			event = decoder.apply(payload);
		} catch (RuntimeException error) {
			emitSignal(IndexerLifecycleProviderSignal.DELIVERY_LOST);
			return;
		}
		observeLag(publishedAt);
		handler.handle(event);
	}

	private void observeLag(long publishedAt) {
		long now = currentTimeMillis.getAsLong();
		if (now >= publishedAt && now - publishedAt > maxTransportLagMs) {
			emitSignal(IndexerLifecycleProviderSignal.EXCESSIVE_LAG);
		}
	}

	private void emitSignal(IndexerLifecycleProviderSignal signal) {
		List<Handler<IndexerLifecycleProviderSignal>> handlers;
		long now = currentTimeMillis.getAsLong();
		synchronized (this) {
			Long previous = lastSignalAt.get(signal);
			if (previous != null && now >= previous
				&& now - previous < signalCooldownMs) {
				return;
			}
			lastSignalAt.put(signal, now);
			handlers = List.copyOf(signalSubscribers);
		}
		handlers.forEach(handler -> handler.handle(signal));
	}

	void reportSignal(IndexerLifecycleProviderSignal signal) {
		emitSignal(Objects.requireNonNull(signal, "signal"));
	}

	private JsonObject envelope(JsonObject payload) {
		return new JsonObject()
			.put(PUBLISHED_AT, currentTimeMillis.getAsLong())
			.put(PAYLOAD, payload);
	}

	static String indexerAddress(String namespace) {
		return ADDRESS_PREFIX + Objects.requireNonNull(namespace, "namespace") + INDEXER_SUFFIX;
	}

	static String targetAddress(String namespace) {
		return ADDRESS_PREFIX + Objects.requireNonNull(namespace, "namespace") + TARGET_SUFFIX;
	}
}
