package com.inqwise.indexer.routing;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.runtime.IndexerQueueClient;
import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.definitions.IndexDefinition;
import com.inqwise.indexer.definitions.IndexerDefinition;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.QueueDefinition;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.RoutedIndexActions;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.hot.InvalidRouteCache;
import com.inqwise.indexer.hot.InvalidRouteInvalidation;
import com.inqwise.indexer.hot.InvalidRouteSignature;
import com.inqwise.indexer.hot.InvalidRouteSignatures;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.TargetPeriodResolver;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.IndexerPlugins;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;

import io.vertx.core.Future;

public class SubmitIndexActionsCommandHandler implements CommandHandler {
	private final MetadataSubmitIndexActionRouter metadataRouter;
	private final TargetDefinitionProvider targetDefinitionProvider;
	private final MetadataChangeNotifier metadataChangeNotifier;
	private final RoutedIndexActionPublisher publisher;
	private final InvalidRouteCache invalidRouteCache;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueClient queue
	) {
		this(metadataRepository, targetDefinitionProvider, metadataChangeNotifier, queue, null);
	}

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueClient queue,
		InvalidRouteCache invalidRouteCache
	) {
		this(metadataRepository, targetDefinitionProvider, metadataChangeNotifier, queue, invalidRouteCache, IndexerPlugins.empty());
	}

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueClient queue,
		InvalidRouteCache invalidRouteCache,
		IndexerPlugins plugins
	) {
		this(
			metadataRepository,
			targetDefinitionProvider,
			metadataChangeNotifier,
			queue,
			invalidRouteCache,
			Objects.requireNonNull(plugins, "plugins").actionReceiveCapabilities()
		);
	}

	public SubmitIndexActionsCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		TargetDefinitionProvider targetDefinitionProvider,
		MetadataChangeNotifier metadataChangeNotifier,
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
			metadataChangeNotifier,
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
		MetadataChangeNotifier metadataChangeNotifier,
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
		this.targetDefinitionProvider = Objects.requireNonNull(
			targetDefinitionProvider,
			"targetDefinitionProvider"
		);
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
		this.publisher = new RoutedIndexActionPublisher(queue);
		this.invalidRouteCache = invalidRouteCache;
	}

	@Override
	public String getType() {
		return SubmitIndexActionsCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		SubmitIndexActionsCommand submit = new SubmitIndexActionsCommand(
			command.toJson(),
			command.getCorrelationId()
		);

		return route(submit)
			.compose(groups -> publish(groups)
				.compose(ignored -> invalidateRoute(submit)))
			.recover(error -> {
				return recordStableInvalidRoute(submit, error)
					.compose(ignored -> Future.failedFuture(error));
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

		return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
			group.indexerId(),
			group.targetId(),
			getType(),
			group.indexerVersion()
		));
	}

	private Future<Void> recordStableInvalidRoute(SubmitIndexActionsCommand submit, Throwable error) {
		if (invalidRouteCache == null || !isStableInvalid(error)) {
			return Future.succeededFuture();
		}

		return invalidRouteSignatures(submit)
			.map(signatures -> {
				for (InvalidRouteSignature signature : signatures) {
					invalidRouteCache.record(signature, error.getMessage());
				}

				return null;
			});
	}

	private Future<Void> invalidateRoute(SubmitIndexActionsCommand submit) {
		if (invalidRouteCache == null) {
			return Future.succeededFuture();
		}

		return invalidRouteSignatures(submit)
			.map(signatures -> {
				for (InvalidRouteSignature signature : signatures) {
					invalidRouteCache.invalidateMatching(new InvalidRouteInvalidation(
						signature.targetName(),
						signature.periodKey(),
						signature.targetId(),
						signature.indexerId(),
						signature.indexName()
					));
				}

				return null;
			});
	}

	private boolean isStableInvalid(Throwable error) {
		return error instanceof CommandFailure failure && failure.stableInvalid();
	}

	private Future<List<InvalidRouteSignature>> invalidRouteSignatures(
		SubmitIndexActionsCommand submit
	) {
		if (submit.getTargetName() == null) {
			return Future.succeededFuture(InvalidRouteSignatures.from(submit));
		}

		return targetDefinitionProvider.getByName(submit.getTargetName())
			.map(found -> found
				.map(targetDefinition -> InvalidRouteSignatures.from(
					submit,
					resolvePeriodKey(targetDefinition, submit)
				))
				.orElseGet(() -> InvalidRouteSignatures.from(submit)))
			.recover(error -> Future.succeededFuture(InvalidRouteSignatures.from(submit)));
	}

	private String resolvePeriodKey(
		TargetDefinition targetDefinition,
		SubmitIndexActionsCommand submit
	) {
		try {
			return periodResolver.resolve(
				targetDefinition.periodStrategy(),
				submit.getTimestamp()
			).key();
		} catch (RuntimeException error) {
			return null;
		}
	}

	private static IndexerDefinitionProvider defaultIndexerDefinitionProvider() {
		IndexerDefinition definition = new IndexerDefinition(
			new IndexDefinition("default", "1", null, null),
			new QueueDefinition(null)
		);
		return ignored -> Future.succeededFuture(definition);
	}
}
