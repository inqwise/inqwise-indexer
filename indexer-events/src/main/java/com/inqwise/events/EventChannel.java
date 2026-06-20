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
}
