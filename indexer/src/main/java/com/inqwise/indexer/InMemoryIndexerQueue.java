package com.inqwise.indexer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import com.inqwise.indexer.definitions.QueueDefinition;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Handler;

public class InMemoryIndexerQueue implements IndexerQueueClient, IndexerQueueResourceManager {
	private final Map<String, QueueState> queuesByName = new HashMap<>();

	@Override
	public Future<IndexerQueuePublisher> publisher(String queueName) {
		QueueState state = ensureState(queueName);
		return Future.succeededFuture(new InMemoryIndexerQueuePublisher(state));
	}

	@Override
	public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
		QueueState state = ensureState(options.getQueueName());
		InMemoryIndexerQueueConsumer consumer = new InMemoryIndexerQueueConsumer(state, options);
		synchronized (this) {
			if (state.consumer != null) {
				state.consumer.close();
			}

			state.consumer = consumer;
		}

		return Future.succeededFuture(consumer);
	}

	public Future<Void> close() {
		synchronized (this) {
			queuesByName.values().forEach(state -> {
				if (state.consumer != null) {
					state.consumer.close();
				}
			});
		}

		return Future.succeededFuture();
	}

	@Override
	public Future<Void> ensure(String queueName) {
		ensureState(queueName);
		return Future.succeededFuture();
	}

	@Override
	public Future<Void> ensure(String queueName, QueueDefinition definition) {
		QueueState state = ensureState(queueName);
		JsonObject settings = definition == null ? new JsonObject() : definition.settings();
		synchronized (this) {
			if (state.settings == null) {
				state.settings = settings.copy();
				return Future.succeededFuture();
			}

			if (state.settings.equals(settings)) {
				return Future.succeededFuture();
			}
		}

		return Future.failedFuture("Queue resource already exists with different settings: " + queueName);
	}

	@Override
	public Future<Void> delete(String queueName) {
		synchronized (this) {
			QueueState state = queuesByName.remove(queueName);
			if (state != null) {
				state.items.clear();
				if (state.consumer != null) {
					state.consumer.close();
				}
			}
		}

		return Future.succeededFuture();
	}

	private QueueState ensureState(String queueName) {
		synchronized (this) {
			return queuesByName.computeIfAbsent(queueName, ignored -> new QueueState());
		}
	}

	private static class QueueState {
		private final Deque<IndexerActionItem> items = new ArrayDeque<>();
		private InMemoryIndexerQueueConsumer consumer;
		private JsonObject settings;
	}

	private class InMemoryIndexerQueuePublisher implements IndexerQueuePublisher {
		private final QueueState state;

		private InMemoryIndexerQueuePublisher(QueueState state) {
			this.state = state;
		}

		@Override
		public Future<Void> publish(IndexerActionItem item) {
			synchronized (InMemoryIndexerQueue.this) {
				state.items.addLast(item);
			}

			if (state.consumer != null) {
				state.consumer.dispatch();
			}

			return Future.succeededFuture();
		}

		@Override
		public Future<Void> close() {
			return Future.succeededFuture();
		}
	}

	private class InMemoryIndexerQueueConsumer implements IndexerQueueConsumer {
		private final QueueState state;
		private Handler<IndexerActionItem> handler;
		private final IndexerQueueConsumerOptions options;
		private IndexerActionItem inFlight;
		private boolean paused = true;
		private boolean closed;

		private InMemoryIndexerQueueConsumer(QueueState state, IndexerQueueConsumerOptions options) {
			this.state = state;
			this.options = options;
		}

		@Override
		public IndexerQueueConsumer handler(Handler<IndexerActionItem> handler) {
			this.handler = handler;
			dispatch();
			return this;
		}

		@Override
		public Future<Void> pause() {
			paused = true;
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> resume() {
			paused = false;
			dispatch();
			return Future.succeededFuture();
		}

		@Override
		public Future<Void> commit() {
			synchronized (InMemoryIndexerQueue.this) {
				if (inFlight != null && state.items.peekFirst() == inFlight) {
					state.items.removeFirst();
				}

				inFlight = null;
			}

			return Future.succeededFuture();
		}

		@Override
		public Future<Void> close() {
			closed = true;
			handler = null;
			inFlight = null;
			return Future.succeededFuture();
		}

		private void dispatch() {
			Handler<IndexerActionItem> currentHandler;
			IndexerActionItem item;

			synchronized (InMemoryIndexerQueue.this) {
				if (closed || paused || inFlight != null || handler == null || state.items.isEmpty()) {
					return;
				}

				currentHandler = handler;
				item = state.items.peekFirst();
				inFlight = item;
			}

			currentHandler.handle(item);
		}

		IndexerQueueConsumerOptions getOptions() {
			return options;
		}
	}
}
