package com.inqwise.events;

import io.vertx.core.Future;

public final class NoopEventPublisher implements EventPublisher {
	@Override
	public <T> Future<Void> publish(EventChannel<T> channel, EventEnvelope<T> event) {
		return Future.succeededFuture();
	}
}
