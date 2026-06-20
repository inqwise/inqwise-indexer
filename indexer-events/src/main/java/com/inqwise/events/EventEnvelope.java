package com.inqwise.events;

import java.time.Instant;
import java.util.Objects;

public record EventEnvelope<T>(
	String eventId,
	String eventType,
	Instant occurredAt,
	String source,
	String correlationId,
	T payload
) {
	public EventEnvelope {
		Objects.requireNonNull(eventId, "eventId");
		Objects.requireNonNull(eventType, "eventType");
		Objects.requireNonNull(occurredAt, "occurredAt");
		Objects.requireNonNull(source, "source");
		Objects.requireNonNull(payload, "payload");
	}
}
