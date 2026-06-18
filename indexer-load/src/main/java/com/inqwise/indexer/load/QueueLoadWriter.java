package com.inqwise.indexer.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.Actions;
import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueuePublisher;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.actions.IndexerActionRouteContext;
import com.inqwise.indexer.actions.IndexerActionRouteMode;

import io.vertx.core.Future;

public class QueueLoadWriter implements LoadWriter {
	private final Integer targetId;
	private final Integer indexerId;
	private final String indexName;
	private final String queueName;
	private final IndexerQueueClient queue;
	private final IndexerLoadRepository loadRepository;

	public QueueLoadWriter(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String queueName,
		IndexerQueueClient queue,
		IndexerLoadRepository loadRepository
	) {
		this.targetId = Objects.requireNonNull(targetId, "targetId");
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.indexName = Objects.requireNonNull(indexName, "indexName");
		this.queueName = Objects.requireNonNull(queueName, "queueName");
		this.queue = Objects.requireNonNull(queue, "queue");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
	}

	@Override
	public Future<Void> submit(List<IndexerActionItem> items) {
		try {
			return publish(normalize(List.copyOf(Objects.requireNonNull(items, "items"))));
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	@Override
	public Future<Void> complete(LoadCompletion completion) {
		return loadRepository.getByIndexerId(indexerId)
			.compose(found -> found
				.map(this::completeIfActive)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId)));
	}

	@Override
	public Future<Void> fail(Throwable error) {
		return loadRepository.getByIndexerId(indexerId)
			.compose(found -> found
				.map(load -> markFailedIfActive(load, error))
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + indexerId)));
	}

	private Future<Void> markFailedIfActive(IndexerLoadRecord load, Throwable error) {
		if (load.state() == IndexerLoadState.FAILED
			|| load.state() == IndexerLoadState.CANCELLED
			|| load.state() == IndexerLoadState.PUBLISHED) {
			return Future.succeededFuture();
		}

		return loadRepository.markFailed(new UpdateIndexerLoadFailure(
			indexerId,
			error == null || error.getMessage() == null
				? "Load provider failed"
				: error.getMessage(),
			null,
			load.version()
		));
	}

	private Future<Void> completeIfActive(IndexerLoadRecord load) {
		if (load.state() != IndexerLoadState.HISTORICAL_LOADING) {
			return Future.succeededFuture();
		}

		return publish(List.of(CompleteIndexActionItem.builder()
			.withTargetId(targetId)
			.withIndexerId(indexerId)
			.build()));
	}

	private Future<Void> publish(List<IndexerActionItem> items) {
		List<IndexerActionItem> batch = new ArrayList<>(items);
		return queue.publisher(queueName)
			.compose(publisher -> publish(publisher, batch)
				.eventually(publisher::close));
	}

	private Future<Void> publish(IndexerQueuePublisher publisher, List<IndexerActionItem> items) {
		Future<Void> published = Future.succeededFuture();
		for (IndexerActionItem item : items) {
			published = published.compose(ignored -> publisher.publish(item));
		}
		return published;
	}

	private List<IndexerActionItem> normalize(List<IndexerActionItem> items) {
		List<IndexerActionItem> normalized = new ArrayList<>(items.size());
		for (IndexerActionItem item : items) {
			normalized.add(normalize(item));
		}
		return normalized;
	}

	private IndexerActionItem normalize(IndexerActionItem item) {
		return switch (item.getActionType()) {
			case PUT_DOCUMENT, REMOVE_DOCUMENT -> Actions.getProvider(item.getActionType())
				.router()
				.route(new IndexerActionRouteContext(
					targetId,
					indexerId,
					null,
					indexName,
					queueName,
					IndexerRole.LOAD_WRITER
				), item, IndexerActionRouteMode.DIRECT)
				.orElseThrow(() -> new IllegalArgumentException("Action is not accepted by load writer"));
			case COMPLETE, CATCH_UP_BARRIER -> throw new IllegalArgumentException(
				"LoadWriter.submit does not accept internal marker action: " + item.getActionType()
			);
		};
	}
}
