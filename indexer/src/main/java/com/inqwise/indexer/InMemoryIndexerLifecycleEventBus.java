package com.inqwise.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Handler;

public class InMemoryIndexerLifecycleEventBus implements IndexerLifecycleEventBus {
	private final List<IndexerMetadataChanged> events = new ArrayList<>();
	private final List<TargetMetadataChanged> targetEvents = new ArrayList<>();
	private final List<Handler<IndexerMetadataChanged>> subscribers = new ArrayList<>();
	private final List<Handler<TargetMetadataChanged>> targetSubscribers = new ArrayList<>();
	private final List<Handler<IndexerLifecycleProviderSignal>> providerSignalSubscribers =
		new ArrayList<>();

	@Override
	public Future<Void> publish(IndexerMetadataChanged event) {
		Objects.requireNonNull(event, "event");

		List<Handler<IndexerMetadataChanged>> handlers;
		synchronized (this) {
			events.add(event);
			handlers = List.copyOf(subscribers);
		}

		handlers.forEach(handler -> handler.handle(event));
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> publish(TargetMetadataChanged event) {
		Objects.requireNonNull(event, "event");

		List<Handler<TargetMetadataChanged>> handlers;
		synchronized (this) {
			targetEvents.add(event);
			handlers = List.copyOf(targetSubscribers);
		}

		handlers.forEach(handler -> handler.handle(event));
		return Future.succeededFuture();
	}

	@Override
	public Future<IndexerLifecycleSubscription> subscribe(
		Handler<IndexerMetadataChanged> handler
	) {
		Objects.requireNonNull(handler, "handler");

		List<IndexerMetadataChanged> replay;
		synchronized (this) {
			subscribers.add(handler);
			replay = List.copyOf(events);
		}

		replay.forEach(handler::handle);
		return Future.succeededFuture(() -> {
			synchronized (this) {
				subscribers.remove(handler);
			}
			return Future.succeededFuture();
		});
	}

	@Override
	public Future<IndexerLifecycleSubscription> subscribeTarget(
		Handler<TargetMetadataChanged> handler
	) {
		Objects.requireNonNull(handler, "handler");

		List<TargetMetadataChanged> replay;
		synchronized (this) {
			targetSubscribers.add(handler);
			replay = List.copyOf(targetEvents);
		}

		replay.forEach(handler::handle);
		return Future.succeededFuture(() -> {
			synchronized (this) {
				targetSubscribers.remove(handler);
			}
			return Future.succeededFuture();
		});
	}

	@Override
	public synchronized Future<IndexerLifecycleSubscription> subscribeProviderSignals(
		Handler<IndexerLifecycleProviderSignal> handler
	) {
		Objects.requireNonNull(handler, "handler");
		providerSignalSubscribers.add(handler);
		return Future.succeededFuture(() -> {
			synchronized (this) {
				providerSignalSubscribers.remove(handler);
			}
			return Future.succeededFuture();
		});
	}

	public void emitProviderSignal(IndexerLifecycleProviderSignal signal) {
		Objects.requireNonNull(signal, "signal");
		List<Handler<IndexerLifecycleProviderSignal>> handlers;
		synchronized (this) {
			handlers = List.copyOf(providerSignalSubscribers);
		}
		handlers.forEach(handler -> handler.handle(signal));
	}

	public synchronized List<IndexerMetadataChanged> events() {
		return List.copyOf(events);
	}

	public synchronized List<TargetMetadataChanged> targetEvents() {
		return List.copyOf(targetEvents);
	}
}
