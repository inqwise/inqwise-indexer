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

	public static <T> Builder<T> builder() {
		return new Builder<>();
	}

	public static final class Builder<T> {
		private String eventId;
		private String eventType;
		private Instant occurredAt;
		private String source;
		private String correlationId;
		private T payload;

		private Builder() {
		}

		public Builder<T> withEventId(String value) {
			eventId = value;
			return this;
		}

		public Builder<T> withEventType(String value) {
			eventType = value;
			return this;
		}

		public Builder<T> withOccurredAt(Instant value) {
			occurredAt = value;
			return this;
		}

		public Builder<T> withSource(String value) {
			source = value;
			return this;
		}

		public Builder<T> withCorrelationId(String value) {
			correlationId = value;
			return this;
		}

		public Builder<T> withPayload(T value) {
			payload = value;
			return this;
		}

		public EventEnvelope<T> build() {
			return new EventEnvelope<>(
				eventId,
				eventType,
				occurredAt,
				source,
				correlationId,
				payload
			);
		}
	}
}
