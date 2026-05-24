package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueuePublisher;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;

import io.vertx.core.Future;

public class SubmitIndexActionsCommandHandler implements CommandHandler {
	private final MetadataSubmitIndexActionRouter metadataRouter;
	private final IndexerLifecycleEventBus eventBus;
	private final IndexerQueueClient queue;

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueClient queue
	) {
		this.metadataRouter = new MetadataSubmitIndexActionRouter(metadataRepository);
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.queue = Objects.requireNonNull(queue, "queue");
	}

	@Override
	public String getType() {
		return SubmitIndexActionsCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		SubmitIndexActionsCommand submit = new SubmitIndexActionsCommand(command.toJson());

		return route(submit)
			.compose(this::publish);
	}

	private Future<List<RoutedIndexActions>> route(SubmitIndexActionsCommand submit) {
		return metadataRouter.route(submit);
	}

	private Future<Void> publish(List<RoutedIndexActions> groups) {
		Future<Void> published = Future.succeededFuture();

		for (RoutedIndexActions group : groups) {
			published = published.compose(ignored -> publishLifecycle(group)
				.compose(publishedLifecycle -> publishActions(group.queueName(), group.actions())));
		}

		return published;
	}

	private Future<Void> publishLifecycle(RoutedIndexActions group) {
		return eventBus.publish(new IndexerMetadataChanged(
			group.indexerId(),
			getType(),
			group.indexerVersion()
		));
	}

	private Future<Void> publishActions(String queueName, List<IndexerActionItem> actions) {
		return queue.publisher(queueName)
			.compose(publisher -> publishActions(publisher, actions)
				.eventually(publisher::close));
	}

	private Future<Void> publishActions(
		IndexerQueuePublisher publisher,
		List<IndexerActionItem> actions
	) {
		List<Future<Void>> publishes = actions.stream()
			.map(publisher::publish)
			.toList();

		return Future.join(publishes).mapEmpty();
	}
}
