package com.inqwise.events;

import java.util.Objects;

public record EventChannel<T>(
	String name,
	Class<T> payloadType
) {
	public EventChannel {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(payloadType, "payloadType");
	}

	public static <T> Builder<T> builder() {
		return new Builder<>();
	}

	public static final class Builder<T> {
		private String name;
		private Class<T> payloadType;

		private Builder() {
		}

		public Builder<T> withName(String value) {
			name = value;
			return this;
		}

		public Builder<T> withPayloadType(Class<T> value) {
			payloadType = value;
			return this;
		}

		public EventChannel<T> build() {
			return new EventChannel<>(name, payloadType);
		}
	}
}
