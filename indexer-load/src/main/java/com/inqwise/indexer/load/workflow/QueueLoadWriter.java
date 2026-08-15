package com.inqwise.indexer.load.workflow;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LoadCompletion;
import com.inqwise.indexer.load.api.LoadWriter;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadFailure;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.Actions;
import com.inqwise.indexer.actions.CompleteIndexActionItem;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.actions.IndexerActionRouteContext;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.routing.IndexerPublishingService;
import com.inqwise.indexer.routing.RoutedIndexActions;

import io.vertx.core.Future;

public class QueueLoadWriter implements LoadWriter {
	private final Integer targetId;
	private final Integer indexerId;
	private final String indexName;
	private final String queueName;
	private final IndexerPublishingService publisher;
	private final IndexerLoadRepository loadRepository;

	public QueueLoadWriter(
		Integer targetId,
		Integer indexerId,
		String indexName,
		String queueName,
		IndexerPublishingService publisher,
		IndexerLoadRepository loadRepository
	) {
		this.targetId = Objects.requireNonNull(targetId, "targetId");
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.indexName = Objects.requireNonNull(indexName, "indexName");
		this.queueName = Objects.requireNonNull(queueName, "queueName");
		this.publisher = Objects.requireNonNull(publisher, "publisher");
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

		return loadRepository.markFailed(UpdateIndexerLoadFailure.builder()
			.withIndexerId(indexerId)
			.withFailureReason(error == null || error.getMessage() == null
				? "Load provider failed"
				: error.getMessage())
			.withExpectedVersion(load.version())
			.build());
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
		return publisher.publish(List.of(RoutedIndexActions.builder()
			.withIndexerId(indexerId)
			.withTargetId(targetId)
			.withIndexerVersion(0L)
			.withQueueName(queueName)
			.withActions(new ArrayList<>(items))
			.build()));
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
				.route(IndexerActionRouteContext.builder()
					.withTargetId(targetId)
					.withIndexerId(indexerId)
					.withIndexName(indexName)
					.withQueueName(queueName)
					.withRole(IndexerRole.LOAD_WRITER)
					.build(), item, IndexerActionRouteMode.DIRECT)
				.orElseThrow(() -> new IllegalArgumentException("Action is not accepted by load writer"));
			case COMPLETE, CATCH_UP_BARRIER -> throw new IllegalArgumentException(
				"LoadWriter.submit does not accept internal marker action: " + item.getActionType()
			);
		};
	}
}
