package com.inqwise.indexer;

import java.util.ArrayDeque;
import java.util.Deque;

import io.vertx.core.Future;
import io.vertx.core.Handler;

public class InMemoryIndexerQueue implements IndexerQueue {
	private final Deque<IndexerActionItem> items = new ArrayDeque<>();
	private final InMemoryIndexerQueueConsumer consumer = new InMemoryIndexerQueueConsumer();

	@Override
	public Future<Void> publish(IndexerActionItem item) {
		synchronized (this) {
			items.addLast(item);
		}

		consumer.dispatch();
		return Future.succeededFuture();
	}

	@Override
	public Future<IndexerQueueConsumer> consumer(IndexerQueueConsumerOptions options) {
		consumer.options = options;
		return Future.succeededFuture(consumer);
	}

	private class InMemoryIndexerQueueConsumer implements IndexerQueueConsumer {
		private Handler<IndexerActionItem> handler;
		private IndexerQueueConsumerOptions options;
		private IndexerActionItem inFlight;
		private boolean paused = true;
		private boolean closed;

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
				if (inFlight != null && items.peekFirst() == inFlight) {
					items.removeFirst();
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
				if (closed || paused || inFlight != null || handler == null || items.isEmpty()) {
					return;
				}

				currentHandler = handler;
				item = items.peekFirst();
				inFlight = item;
			}

			currentHandler.handle(item);
		}

		IndexerQueueConsumerOptions getOptions() {
			return options;
		}
	}
}
