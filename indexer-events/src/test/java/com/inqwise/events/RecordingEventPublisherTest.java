package com.inqwise.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class RecordingEventPublisherTest {
	@Test
	void recordsAcceptedEvent() {
		RecordingEventPublisher publisher = new RecordingEventPublisher();
		EventChannel<String> channel = new EventChannel<>("test.events", String.class);
		EventEnvelope<String> event = new EventEnvelope<>(
			"event-1",
			"TEST_EVENT",
			Instant.parse("2026-06-19T10:00:00Z"),
			"test",
			"command-1",
			"payload"
		);

		assertTrue(publisher.publish(channel, event).succeeded());
		assertEquals(1, publisher.publishedEvents().size());
		assertEquals(channel, publisher.publishedEvents().get(0).channel());
		assertEquals(event, publisher.publishedEvents().get(0).event());
	}

	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
	void rejectsPayloadThatDoesNotMatchChannelType() {
		RecordingEventPublisher publisher = new RecordingEventPublisher();
		EventChannel channel = new EventChannel<>("test.events", String.class);
		EventEnvelope event = new EventEnvelope<>(
			"event-1",
			"TEST_EVENT",
			Instant.parse("2026-06-19T10:00:00Z"),
			"test",
			null,
			42
		);

		assertTrue(publisher.publish(channel, event).failed());
		assertTrue(publisher.publishedEvents().isEmpty());
	}
}
