package com.inqwise.events;

import io.vertx.core.Future;

public interface EventPublisher {
	EventPublisher NOOP = new NoopEventPublisher();

	<T> Future<Void> publish(EventChannel<T> channel, EventEnvelope<T> event);
}
