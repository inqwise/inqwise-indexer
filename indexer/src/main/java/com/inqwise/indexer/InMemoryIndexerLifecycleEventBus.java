package com.inqwise.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.core.Handler;

public class InMemoryIndexerLifecycleEventBus implements IndexerLifecycleEventBus {
	private final List<IndexerLifecycleChanged> events = new ArrayList<>();
	private final List<Handler<IndexerLifecycleChanged>> subscribers = new ArrayList<>();

	@Override
	public Future<Void> publish(IndexerLifecycleChanged event) {
		Objects.requireNonNull(event, "event");

		List<Handler<IndexerLifecycleChanged>> handlers;
		synchronized (this) {
			events.add(event);
			handlers = List.copyOf(subscribers);
		}

		handlers.forEach(handler -> handler.handle(event));
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> subscribe(Handler<IndexerLifecycleChanged> handler) {
		Objects.requireNonNull(handler, "handler");

		List<IndexerLifecycleChanged> replay;
		synchronized (this) {
			subscribers.add(handler);
			replay = List.copyOf(events);
		}

		replay.forEach(handler::handle);
		return Future.succeededFuture();
	}
}
