package com.inqwise.indexer.node.application.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.runtime.IndexerEvent;
import com.inqwise.indexer.runtime.IndexerEventType;

import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.vertx.core.json.JsonObject;

class MicrometerIndexerEventPublisherTest {
	@Test
	void recordsBoundedRuntimeEventsAndActiveGauge() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		MicrometerIndexerEventPublisher publisher =
			new MicrometerIndexerEventPublisher(registry);
		IndexerModel model = model(IndexerRole.LIVE_WRITER);

		publisher.publish(event(IndexerEventType.INDEXER_STARTED, model, null));
		publisher.publish(event(IndexerEventType.INDEXER_STARTED, model, null));

		assertEquals(
			2,
			registry.get(MicrometerIndexerEventPublisher.RUNTIME_EVENTS)
				.tags("event", "indexer_started", "role", "live_writer")
				.counter()
				.count()
		);
		assertEquals(
			1,
			registry.get(MicrometerIndexerEventPublisher.ACTIVE_RUNTIMES)
				.tag("role", "live_writer")
				.gauge()
				.value()
		);

		publisher.publish(event(IndexerEventType.INDEXER_STOPPED, model, null));
		publisher.publish(event(IndexerEventType.INDEXER_STOPPED, model, null));

		assertEquals(
			0,
			registry.get(MicrometerIndexerEventPublisher.ACTIVE_RUNTIMES)
				.tag("role", "live_writer")
				.gauge()
				.value()
		);
	}

	@Test
	void recordsCompletedAndFailedActionProcessingDurations() {
		MockClock clock = new MockClock();
		SimpleMeterRegistry registry =
			new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
		MicrometerIndexerEventPublisher publisher =
			new MicrometerIndexerEventPublisher(registry);
		IndexerModel model = model(IndexerRole.LOAD_WRITER);
		IndexerActionItem completed = action("completed");
		IndexerActionItem failed = action("failed");

		publisher.publish(event(
			IndexerEventType.ACTION_ITEM_PROCESSING_STARTED,
			model,
			completed
		));
		clock.add(Duration.ofMillis(25));
		publisher.publish(event(
			IndexerEventType.ACTION_ITEM_PROCESSING_COMPLETED,
			model,
			completed
		));
		publisher.publish(event(
			IndexerEventType.ACTION_ITEM_PROCESSING_STARTED,
			model,
			failed
		));
		clock.add(Duration.ofMillis(40));
		publisher.publish(event(IndexerEventType.ACTION_ITEM_FAILED, model, failed));

		assertEquals(
			25,
			registry.get(MicrometerIndexerEventPublisher.ACTION_PROCESSING)
				.tags(
					"action", "put_document",
					"outcome", "completed",
					"role", "load_writer"
				)
				.timer()
				.totalTime(TimeUnit.MILLISECONDS)
		);
		assertEquals(
			40,
			registry.get(MicrometerIndexerEventPublisher.ACTION_PROCESSING)
				.tags(
					"action", "put_document",
					"outcome", "failed",
					"role", "load_writer"
				)
				.timer()
				.totalTime(TimeUnit.MILLISECONDS)
		);
	}

	private static IndexerEvent event(
		IndexerEventType type,
		IndexerModel model,
		IndexerActionItem item
	) {
		return IndexerEvent.builder()
			.withType(type)
			.withModel(model)
			.withItem(item)
			.build();
	}

	private static IndexerModel model(IndexerRole role) {
		return IndexerModel.builder()
			.withIndexName("test-index")
			.withRole(role)
			.build();
	}

	private static IndexerActionItem action(String uid) {
		return PutDocumentActionItem.builder()
			.withUid(uid)
			.withDocument(new JsonObject().put("value", uid))
			.build();
	}
}
