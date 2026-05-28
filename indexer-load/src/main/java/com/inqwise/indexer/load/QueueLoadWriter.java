package com.inqwise.indexer.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueuePublisher;

import io.vertx.core.Future;

public class QueueLoadWriter implements LoadWriter {
	private final Integer targetId;
	private final Integer loadIndexerId;
	private final String queueName;
	private final IndexerQueueClient queue;
	private final IndexerLoadRepository loadRepository;

	public QueueLoadWriter(
		Integer targetId,
		Integer loadIndexerId,
		String queueName,
		IndexerQueueClient queue,
		IndexerLoadRepository loadRepository
	) {
		this.targetId = Objects.requireNonNull(targetId, "targetId");
		this.loadIndexerId = Objects.requireNonNull(loadIndexerId, "loadIndexerId");
		this.queueName = Objects.requireNonNull(queueName, "queueName");
		this.queue = Objects.requireNonNull(queue, "queue");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
	}

	@Override
	public Future<Void> submit(List<IndexerActionItem> items) {
		return publish(List.copyOf(Objects.requireNonNull(items, "items")));
	}

	@Override
	public Future<Void> complete(LoadCompletion completion) {
		return publish(List.of(CompleteIndexActionItem.builder()
			.withTargetId(targetId)
			.withIndexerId(loadIndexerId)
			.build()));
	}

	@Override
	public Future<Void> fail(Throwable error) {
		return loadRepository.getByLoadIndexerId(loadIndexerId)
			.compose(found -> found
				.map(load -> loadRepository.markFailed(new UpdateIndexerLoadFailure(
					loadIndexerId,
					error == null || error.getMessage() == null
						? "Load provider failed"
						: error.getMessage(),
					null,
					load.version()
				)))
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + loadIndexerId)));
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
}
