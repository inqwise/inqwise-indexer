package com.inqwise.indexer.node.application.monitoring;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.runtime.IndexerEvent;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.runtime.IndexerEventType;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.Future;

public final class MicrometerIndexerEventPublisher implements IndexerEventPublisher {
	public static final String RUNTIME_EVENTS = "inqwise.indexer.runtime.events";
	public static final String ACTIVE_RUNTIMES = "inqwise.indexer.runtime.active";
	public static final String ACTION_PROCESSING = "inqwise.indexer.action.processing";

	private final MeterRegistry registry;
	private final Map<IndexerRole, AtomicInteger> activeRuntimes =
		new EnumMap<>(IndexerRole.class);
	private final Set<IndexerModel> activeModels =
		Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<ProcessingKey, Timer.Sample> processingSamples =
		new ConcurrentHashMap<>();

	public MicrometerIndexerEventPublisher(MeterRegistry registry) {
		this.registry = Objects.requireNonNull(registry, "registry");
		for (IndexerRole role : IndexerRole.values()) {
			AtomicInteger active = new AtomicInteger();
			activeRuntimes.put(role, active);
			Gauge.builder(ACTIVE_RUNTIMES, active, AtomicInteger::get)
				.description("Active indexer runtimes in this node")
				.tag("role", tag(role))
				.register(registry);
		}
	}

	public static IndexerEventPublisher create(MeterRegistry registry) {
		if (registry == null) {
			return IndexerEventPublisher.NOOP;
		}
		try {
			return new MicrometerIndexerEventPublisher(registry);
		} catch (RuntimeException ignored) {
			return IndexerEventPublisher.NOOP;
		}
	}

	@Override
	public Future<Void> publish(IndexerEvent event) {
		try {
			record(Objects.requireNonNull(event, "event"));
		} catch (RuntimeException ignored) {
			// Metrics are operational signals and must not fail runtime processing.
		}
		return Future.succeededFuture();
	}

	private void record(IndexerEvent event) {
		registry.counter(
			RUNTIME_EVENTS,
			"event", tag(event.getType()),
			"role", tag(event.getModel().getRole())
		).increment();

		switch (event.getType()) {
			case INDEXER_STARTED -> activate(event.getModel());
			case INDEXER_STOPPED -> deactivate(event.getModel());
			case ACTION_ITEM_PROCESSING_STARTED -> startProcessing(event);
			case ACTION_ITEM_PROCESSING_COMPLETED -> stopProcessing(event, "completed");
			case ACTION_ITEM_FAILED -> stopProcessing(event, "failed");
			default -> {
			}
		}
	}

	private void activate(IndexerModel model) {
		synchronized (activeModels) {
			if (activeModels.add(model)) {
				activeRuntimes.get(model.getRole()).incrementAndGet();
			}
		}
	}

	private void deactivate(IndexerModel model) {
		synchronized (activeModels) {
			if (activeModels.remove(model)) {
				activeRuntimes.get(model.getRole()).decrementAndGet();
			}
		}
		processingSamples.keySet().removeIf(key -> key.model == model);
	}

	private void startProcessing(IndexerEvent event) {
		IndexerActionItem item = event.getItem();
		if (item != null) {
			processingSamples.put(
				new ProcessingKey(event.getModel(), item),
				Timer.start(registry)
			);
		}
	}

	private void stopProcessing(IndexerEvent event, String outcome) {
		IndexerActionItem item = event.getItem();
		if (item == null) {
			return;
		}
		Timer.Sample sample = processingSamples.remove(
			new ProcessingKey(event.getModel(), item)
		);
		if (sample == null) {
			return;
		}
		sample.stop(Timer.builder(ACTION_PROCESSING)
			.description("Indexer action-item processing duration")
			.tag("action", tag(item.getActionType()))
			.tag("outcome", outcome)
			.tag("role", tag(event.getModel().getRole()))
			.register(registry));
	}

	private static String tag(Enum<?> value) {
		return value.name().toLowerCase(Locale.ROOT);
	}

	private static final class ProcessingKey {
		private final IndexerModel model;
		private final IndexerActionItem item;

		private ProcessingKey(IndexerModel model, IndexerActionItem item) {
			this.model = model;
			this.item = item;
		}

		@Override
		public boolean equals(Object value) {
			return value instanceof ProcessingKey other
				&& model == other.model
				&& item == other.item;
		}

		@Override
		public int hashCode() {
			return 31 * System.identityHashCode(model) + System.identityHashCode(item);
		}
	}
}
