package com.inqwise.indexer.runtime;

import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;

public class IndexerProcessorVerticle extends VerticleBase {
	private final IndexerModel model;
	private final IndexerOptions options;
	private final IndexerQueueClient queue;
	private final ActionItemProcessHandler processHandler;
	private final IndexerEventPublisher eventPublisher;
	private IndexerQueueConsumer consumer;

	public IndexerProcessorVerticle(
		IndexerModel model,
		IndexerOptions options,
		IndexerQueueClient queue,
		ActionItemProcessHandler processHandler,
		IndexerEventPublisher eventPublisher
	) {
		this.model = Objects.requireNonNull(model, "model");
		this.options = options == null ? IndexerOptions.builder().build() : options;
		this.queue = Objects.requireNonNull(queue, "queue");
		this.processHandler = Objects.requireNonNull(processHandler, "processHandler");
		this.eventPublisher = eventPublisher == null ? IndexerEventPublisher.NOOP : eventPublisher;
	}

	@Override
	public Future<?> start() {
		IndexerQueueConsumerOptions consumerOptions = IndexerQueueConsumerOptions.builder()
			.withTargetName(model.getTargetName())
			.withQueueName(getQueueName())
			.withBulkSize(options.getBulkSize())
			.build();

		return queue.consumer(consumerOptions)
			.compose(created -> {
				consumer = created.handler(this::onActionItem);
				return consumer.resume()
					.compose(ignored -> emitEvent(IndexerEventType.CONSUMER_RESUMED, null, null));
			});
	}

	@Override
	public Future<?> stop() {
		Future<Void> close = consumer == null ? Future.succeededFuture() : consumer.close();
		consumer = null;
		return close.compose(ignored -> emitEvent(IndexerEventType.INDEXER_STOPPED, null, null));
	}

	private void onActionItem(IndexerActionItem item) {
		emitEvent(IndexerEventType.ACTION_ITEM_RECEIVED, item, null)
			.compose(ignored -> consumer.pause())
			.compose(ignored -> emitEvent(IndexerEventType.CONSUMER_PAUSED, item, null))
			.compose(ignored -> emitEvent(IndexerEventType.ACTION_ITEM_PROCESSING_STARTED, item, null))
			.compose(ignored -> processHandler.process(item))
			.compose(ignored -> emitEvent(IndexerEventType.ACTION_ITEM_PROCESSING_COMPLETED, item, null))
			.compose(ignored -> consumer.commit())
			.compose(ignored -> emitEvent(IndexerEventType.ACTION_ITEM_COMMITTED, item, null))
			.compose(ignored -> consumer.resume())
			.compose(ignored -> emitEvent(IndexerEventType.CONSUMER_RESUMED, item, null))
			.onFailure(error -> emitEvent(IndexerEventType.ACTION_ITEM_FAILED, item, error));
	}

	private Future<Void> emitEvent(IndexerEventType type, IndexerActionItem item, Throwable error) {
		return eventPublisher.publish(IndexerEvent.builder()
			.withType(type)
			.withModel(model)
			.withItem(item)
			.withError(error)
			.build());
	}

	private String getQueueName() {
		return model.getQueueName() == null
			? options.getQueueNamePrefix() + model.getIndexName()
			: model.getQueueName();
	}
}
