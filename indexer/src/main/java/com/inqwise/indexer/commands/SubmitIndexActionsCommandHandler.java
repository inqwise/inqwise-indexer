package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerQueueClient;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.definitions.StaticIndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.hot.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteInvalidation;
import com.inqwise.indexer.hot.InvalidRouteSignature;
import com.inqwise.indexer.hot.InvalidRouteSignatures;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;

public class SubmitIndexActionsCommandHandler implements CommandHandler {
	private final MetadataSubmitIndexActionRouter metadataRouter;
	private final IndexerLifecycleEventBus eventBus;
	private final RoutedIndexActionPublisher publisher;
	private final InvalidRouteCache invalidRouteCache;

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueClient queue
	) {
		this(metadataRepository, targetDefinitionProvider, eventBus, queue, null);
	}

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueClient queue,
		InvalidRouteCache invalidRouteCache
	) {
		this(metadataRepository, targetDefinitionProvider, eventBus, queue, invalidRouteCache, List.of());
	}

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueClient queue,
		InvalidRouteCache invalidRouteCache,
		List<IndexerActionReceiveCapability> receiveCapabilities
	) {
		this(
			metadataRepository,
			targetDefinitionProvider,
			defaultIndexerDefinitionProvider(),
			IndexerDocumentIndexResourceManager.NOOP,
			IndexerQueueResourceManager.NOOP,
			eventBus,
			queue,
			invalidRouteCache,
			receiveCapabilities
		);
	}

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		IndexerLifecycleEventBus eventBus,
		IndexerQueueClient queue,
		InvalidRouteCache invalidRouteCache,
		List<IndexerActionReceiveCapability> receiveCapabilities
	) {
		this.metadataRouter = new MetadataSubmitIndexActionRouter(
			metadataRepository,
			targetDefinitionProvider,
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources,
			receiveCapabilities
		);
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.publisher = new RoutedIndexActionPublisher(queue);
		this.invalidRouteCache = invalidRouteCache;
	}

	@Override
	public String getType() {
		return SubmitIndexActionsCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		SubmitIndexActionsCommand submit = new SubmitIndexActionsCommand(command.toJson());

		return route(submit)
			.compose(groups -> publish(groups)
				.onSuccess(ignored -> invalidateRoute(submit)))
			.recover(error -> {
				recordStableInvalidRoute(submit, error);
				return Future.failedFuture(error);
			});
	}

	private Future<List<RoutedIndexActions>> route(SubmitIndexActionsCommand submit) {
		return metadataRouter.route(submit);
	}

	private Future<Void> publish(List<RoutedIndexActions> groups) {
		Future<Void> published = Future.succeededFuture();

		for (RoutedIndexActions group : groups) {
			published = published.compose(ignored -> publishMetadataChanged(group)
				.compose(publishedLifecycle -> publisher.publish(List.of(group))));
		}

		return published;
	}

	private Future<Void> publishMetadataChanged(RoutedIndexActions group) {
		if (!group.metadataChanged()) {
			return Future.succeededFuture();
		}

		return eventBus.publish(new IndexerMetadataChanged(
			group.indexerId(),
			getType(),
			group.indexerVersion()
		));
	}

	private void recordStableInvalidRoute(SubmitIndexActionsCommand submit, Throwable error) {
		if (invalidRouteCache == null || !isStableInvalid(error)) {
			return;
		}

		for (InvalidRouteSignature signature : InvalidRouteSignatures.from(submit)) {
			invalidRouteCache.record(signature, error.getMessage());
		}
	}

	private void invalidateRoute(SubmitIndexActionsCommand submit) {
		if (invalidRouteCache == null) {
			return;
		}

		for (InvalidRouteSignature signature : InvalidRouteSignatures.from(submit)) {
			invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
				signature.targetName(),
				signature.periodKey(),
				signature.targetId(),
				signature.indexerId(),
				signature.indexName()
			));
		}
	}

	private boolean isStableInvalid(Throwable error) {
		return error instanceof CommandFailure failure && failure.stableInvalid();
	}

	private static IndexerDefinitionProvider defaultIndexerDefinitionProvider() {
		return new StaticIndexerDefinitionProvider(new IndexerDefinition(
			new IndexDefinition("default", "1", null, null),
			new QueueDefinition(null)
		));
	}
}
