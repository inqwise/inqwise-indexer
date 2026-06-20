package com.inqwise.events;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import io.vertx.core.Future;

public final class RecordingEventPublisher implements EventPublisher {
	private final List<PublishedEvent<?>> publishedEvents = new CopyOnWriteArrayList<>();

	@Override
	public <T> Future<Void> publish(EventChannel<T> channel, EventEnvelope<T> event) {
		Objects.requireNonNull(channel, "channel");
		Objects.requireNonNull(event, "event");

		if (!channel.payloadType().isInstance(event.payload())) {
			return Future.failedFuture(new IllegalArgumentException(
				"Event payload does not match channel type: " + channel.name()
			));
		}

		publishedEvents.add(new PublishedEvent<>(channel, event));
		return Future.succeededFuture();
	}

	public List<PublishedEvent<?>> publishedEvents() {
		return List.copyOf(publishedEvents);
	}

	public record PublishedEvent<T>(
		EventChannel<T> channel,
		EventEnvelope<T> event
	) {
	}
}
