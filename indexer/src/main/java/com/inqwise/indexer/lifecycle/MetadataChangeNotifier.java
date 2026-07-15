package com.inqwise.indexer.lifecycle;

import java.util.Objects;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;

import io.vertx.core.Future;

public final class MetadataChangeNotifier {
	private final TargetInvalidationRegistry invalidationRegistry;
	private final IndexerLifecycleEventBus eventBus;

	public MetadataChangeNotifier(
		TargetInvalidationRegistry invalidationRegistry,
		IndexerLifecycleEventBus eventBus
	) {
		this.invalidationRegistry = Objects.requireNonNull(
			invalidationRegistry,
			"invalidationRegistry"
		);
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	public Future<Void> indexerChanged(IndexerMetadataChanged event) {
		Objects.requireNonNull(event, "event");
		return invalidationRegistry.markInvalidated(event.getTargetId())
			.map(ignored -> {
				eventBus.publishIndexerWakeUp(event);
				return null;
			});
	}

	public Future<Void> confirmTargetInvalidated(Integer concreteTargetId) {
		return invalidationRegistry.markInvalidated(
			Objects.requireNonNull(concreteTargetId, "concreteTargetId")
		);
	}

	public Future<Void> targetChanged(TargetMetadataChanged event) {
		Objects.requireNonNull(event, "event");
		return invalidationRegistry.markInvalidated(event.getTargetId())
			.map(ignored -> {
				eventBus.publishTargetWakeUp(event);
				return null;
			});
	}
}
