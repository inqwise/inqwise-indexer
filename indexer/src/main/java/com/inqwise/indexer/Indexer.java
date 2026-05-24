package com.inqwise.indexer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public class Indexer {
	protected final Vertx vertx;
	protected final IndexerModel model;
	protected final Indexer nextIndexer;
	protected final IndexerDocumentStore documentStore;
	protected final IndexerOptions options;
	protected final IndexerQueueClient queue;
	protected final IndexerEventPublisher eventPublisher;
	protected IndexerProcessor processor;

	private MessageConsumer<JsonObject> queueConsumer;
	private IndexerQueueConsumer actionConsumer;
	private IndexerQueuePublisher publisher;
	private Future<Void> activation;

	public Indexer(Vertx vertx, IndexerModel model, IndexerDocumentStore documentStore) {
		this(vertx, model, null, documentStore, new IndexerOptions());
	}

	public Indexer(
		Vertx vertx,
		IndexerModel model,
		Indexer nextIndexer,
		IndexerDocumentStore documentStore,
		IndexerOptions options
	) {
		this(vertx, model, nextIndexer, documentStore, options, null, IndexerEventPublisher.NOOP);
	}

	public Indexer(
		Vertx vertx,
		IndexerModel model,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher
	) {
		this(vertx, model, null, documentStore, options, queue, eventPublisher);
	}

	public Indexer(
		Vertx vertx,
		IndexerModel model,
		Indexer nextIndexer,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher
	) {
		this(vertx, model, nextIndexer, documentStore, options, queue, eventPublisher);
	}

	private Indexer(
		Vertx vertx,
		IndexerModel model,
		Indexer nextIndexer,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerQueueClient queue,
		IndexerEventPublisher eventPublisher
	) {
		this(vertx, model, nextIndexer, documentStore, options, queue, eventPublisher, null);
	}

	public Indexer(
		Vertx vertx,
		IndexerModel model,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher,
		IndexerProcessor processor
	) {
		this(vertx, model, null, documentStore, options, queue, eventPublisher, processor);
	}

	public Indexer(
		Vertx vertx,
		IndexerModel model,
		IndexerQueueClient queue,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerEventPublisher eventPublisher,
		IndexerProcessorFactory processorFactory
	) {
		this(
			vertx,
			model,
			null,
			documentStore,
			options,
			queue,
			eventPublisher,
			null
		);

		this.processor = Objects.requireNonNull(processorFactory, "processorFactory")
			.create(this.model, this.options, this::processActionItem, this.eventPublisher);
	}

	private Indexer(
		Vertx vertx,
		IndexerModel model,
		Indexer nextIndexer,
		IndexerDocumentStore documentStore,
		IndexerOptions options,
		IndexerQueueClient queue,
		IndexerEventPublisher eventPublisher,
		IndexerProcessor processor
	) {
		this.vertx = Objects.requireNonNull(vertx, "vertx");
		this.model = Objects.requireNonNull(model, "model");
		this.nextIndexer = nextIndexer;
		this.documentStore = Objects.requireNonNull(documentStore, "documentStore");
		this.options = options == null ? new IndexerOptions() : options;
		this.queue = queue;
		this.eventPublisher = eventPublisher == null ? IndexerEventPublisher.NOOP : eventPublisher;
		this.processor = processor;
	}

	public synchronized Future<Void> activate() {
		if (!model.getRuntimeState().isActive()) {
			return Future.succeededFuture();
		}

		if (activation != null) {
			return activation;
		}

		Future<Void> nextActivation = nextIndexer == null ? Future.succeededFuture() : nextIndexer.activate();
		activation = nextActivation
			.compose(ignored -> openConsumer())
			.compose(ignored -> emitEvent(IndexerEventType.INDEXER_STARTED, null, null))
			.onFailure(ignored -> clearActivation());

		return activation;
	}

	private synchronized void clearActivation() {
		activation = null;
	}

	protected Future<Void> startActionConsumer() {
		if (actionConsumer != null) {
			return Future.succeededFuture();
		}

		IndexerQueueConsumerOptions consumerOptions = new IndexerQueueConsumerOptions(
			model.getTargetName(),
			getQueueName(),
			options.getBulkSize()
		);

		return queue.consumer(consumerOptions)
			.compose(consumer -> {
				actionConsumer = consumer.handler(this::onActionItem);
				return actionConsumer.resume()
					.compose(ignored -> emitEvent(IndexerEventType.CONSUMER_RESUMED, null, null));
			});
	}

	protected Future<Void> startListeners() {
		if (queue != null) {
			return startActionConsumer();
		}

		if (queueConsumer != null) {
			return Future.succeededFuture();
		}

		queueConsumer = vertx.eventBus()
			.<JsonObject>consumer(getQueueName())
			.handler(this::onQueueItem)
			.exceptionHandler(Throwable::printStackTrace);

		return Future.succeededFuture();
	}

	protected String getQueueName() {
		return model.getQueueName() == null
			? options.getQueueNamePrefix() + model.getIndexName()
			: model.getQueueName();
	}

	protected void onQueueItem(Message<JsonObject> message) {
		Future<Void> result = indexAction(IndexerActionItem.fromJson(message.body()));

		result
			.onSuccess(ignored -> message.reply(new JsonObject()))
			.onFailure(error -> message.fail(1, error.getMessage()));
	}

	protected void onActionItem(IndexerActionItem item) {
		emitEvent(IndexerEventType.ACTION_ITEM_RECEIVED, item, null)
			.compose(ignored -> actionConsumer.pause())
			.compose(ignored -> emitEvent(IndexerEventType.CONSUMER_PAUSED, item, null))
			.compose(ignored -> emitEvent(IndexerEventType.ACTION_ITEM_PROCESSING_STARTED, item, null))
			.compose(ignored -> processActionItem(item))
			.compose(ignored -> emitEvent(IndexerEventType.ACTION_ITEM_PROCESSING_COMPLETED, item, null))
			.compose(ignored -> actionConsumer.commit())
			.compose(ignored -> emitEvent(IndexerEventType.ACTION_ITEM_COMMITTED, item, null))
			.compose(ignored -> actionConsumer.resume())
			.compose(ignored -> emitEvent(IndexerEventType.CONSUMER_RESUMED, item, null))
			.onFailure(error -> emitEvent(IndexerEventType.ACTION_ITEM_FAILED, item, error));
	}

	protected Future<Void> processActionItem(IndexerActionItem item) {
		if (item.getActionType() == IndexerActionType.COMPLETE) {
			return completeIndexActionNotImplemented();
		}

		String validationError = validateActionIdentity(item);
		if (validationError != null) {
			return Future.failedFuture(validationError);
		}

		return Actions.getProvider(item.getActionType())
			.action()
			.process(model, documentStore, item);
	}

	protected Future<Void> emitEvent(IndexerEventType type, IndexerActionItem item, Throwable error) {
		return eventPublisher.publish(new IndexerEvent(type, model, item, error));
	}

	protected Future<Void> indexAction(IndexerActionItem item) {
		String validationError = validateActionIdentity(item);
		if (validationError != null) {
			return Future.failedFuture(validationError);
		}

		return switch (item.getActionType()) {
			case PUT_DOCUMENT -> {
				PutDocumentActionItem put = (PutDocumentActionItem) item;
				yield documentStore.put(put.getIndexName(), put.getUid(), put.getDocument());
			}
			case REMOVE_DOCUMENT -> {
				RemoveDocumentActionItem remove = (RemoveDocumentActionItem) item;
				yield documentStore.remove(getRemoveIndexName(remove), remove.getUid());
			}
			case COMPLETE -> completeIndexActionNotImplemented();
		};
	}

	private Future<Void> completeIndexActionNotImplemented() {
		return Future.failedFuture(new UnsupportedOperationException(
			"Complete index action flow is not implemented"
		));
	}

	private String validateActionIdentity(IndexerActionItem item) {
		return switch (item.getActionType()) {
			case PUT_DOCUMENT -> validatePutIdentity((PutDocumentActionItem) item);
			case REMOVE_DOCUMENT -> validateRemoveIdentity((RemoveDocumentActionItem) item);
			case COMPLETE -> validateCompleteIdentity((CompleteIndexActionItem) item);
		};
	}

	private String validatePutIdentity(PutDocumentActionItem item) {
		String error = validateTargetId(item.getTargetId());
		if (error != null) {
			return error;
		}

		error = validateIndexerId(item.getIndexerId());
		if (error != null) {
			return error;
		}

		return validateIndexName(item.getIndexName());
	}

	private String validateRemoveIdentity(RemoveDocumentActionItem item) {
		String error = validateTargetId(item.getTargetId());
		if (error != null) {
			return error;
		}

		error = validateIndexerId(item.getIndexerId());
		if (error != null) {
			return error;
		}

		return item.getIndexName() == null ? null : validateIndexName(item.getIndexName());
	}

	private String validateCompleteIdentity(CompleteIndexActionItem item) {
		String error = validateTargetId(item.getTargetId());
		if (error != null) {
			return error;
		}

		return validateIndexerId(item.getIndexerId());
	}

	private String validateTargetId(Integer targetId) {
		if (targetId != null && model.getTargetId() != null && !targetId.equals(model.getTargetId())) {
			return "Action target id mismatch for indexer: " + model.getIndexName();
		}

		return null;
	}

	private String validateIndexerId(Integer indexerId) {
		if (indexerId != null && model.getId() != null && !indexerId.equals(model.getId())) {
			return "Action indexer id mismatch for indexer: " + model.getIndexName();
		}

		return null;
	}

	private String validateIndexName(String indexName) {
		if (!model.getIndexName().equals(indexName)) {
			return "Action index mismatch for indexer: " + model.getIndexName();
		}

		return null;
	}

	private String getRemoveIndexName(RemoveDocumentActionItem item) {
		return item.getIndexName() == null ? model.getIndexName() : item.getIndexName();
	}

	public Future<Void> index(List<IndexerActionItem> actions) {
		if (!model.getRuntimeState().isActive()) {
			return Future.failedFuture("indexer is not active: " + model.toJson().encode());
		}

		List<Future<Void>> futures = actions.stream()
			.map(this::submit)
			.collect(Collectors.toList());

		return Future.join(futures).mapEmpty();
	}

	public synchronized Future<Void> openProducer() {
		if (queue == null) {
			return Future.succeededFuture();
		}

		if (publisher != null) {
			return Future.succeededFuture();
		}

		return queue.publisher(getQueueName())
			.onSuccess(created -> publisher = created)
			.mapEmpty();
	}

	public synchronized Future<Void> closeProducer() {
		if (publisher == null) {
			return Future.succeededFuture();
		}

		IndexerQueuePublisher closing = publisher;
		publisher = null;
		return closing.close();
	}

	public Future<Void> submit(IndexerActionItem item) {
		if (queue == null) {
			return enqueueItem(item.toJson());
		}

		return openProducer()
			.compose(ignored -> publisher.publish(item));
	}

	public Future<Void> openConsumer() {
		return processor == null ? startListeners() : processor.open();
	}

	public Future<Void> closeConsumer() {
		return processor == null ? unregisterCurrent() : closeProcessor();
	}

	private Future<Void> closeProcessor() {
		clearActivation();
		return processor.close()
			.compose(ignored -> emitEvent(IndexerEventType.INDEXER_STOPPED, null, null));
	}

	protected Future<Void> enqueueItem(JsonObject item) {
		return vertx.eventBus().request(getQueueName(), item).mapEmpty();
	}

	public IndexerSnapshot status() {
		return new IndexerSnapshot(
			model,
			getQueueName(),
			nextIndexer == null ? null : nextIndexer.status()
		);
	}

	public Future<IndexerModel> delete() {
		return close()
			.map(model);
	}

	public synchronized Future<Void> unregister() {
		Future<Void> close = closeConsumer();

		if (nextIndexer != null) {
			close = close.compose(ignored -> nextIndexer.unregister());
		}

		return close;
	}

	protected synchronized Future<Void> unregisterCurrent() {
		Future<Void> close = queueConsumer == null ? Future.succeededFuture() : queueConsumer.unregister();
		queueConsumer = null;
		activation = null;

		if (actionConsumer != null) {
			close = close.compose(ignored -> actionConsumer.close());
			actionConsumer = null;
		}

		return close.compose(ignored -> emitEvent(IndexerEventType.INDEXER_STOPPED, null, null));
	}

	public Future<Void> close() {
		return closeConsumer()
			.compose(ignored -> closeProducer());
	}
}
