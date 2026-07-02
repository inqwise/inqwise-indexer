package com.inqwise.indexer.provisioning;

import java.util.Objects;

import com.inqwise.indexer.IndexerMetadataChanged;
import com.inqwise.indexer.IndexerQueueResourceManager;
import com.inqwise.indexer.MetadataChangeNotifier;
import com.inqwise.indexer.TargetMetadataChanged;
import com.inqwise.indexer.commands.CreateTargetCommand;
import com.inqwise.indexer.commands.InitialPublicationMode;
import com.inqwise.indexer.commands.PublishIndexCommand;
import com.inqwise.indexer.commands.PublishIndexCommandHandler;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinition;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.metadata.ConcreteTargetKey;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.PublicationRecord;
import com.inqwise.indexer.metadata.ReadinessState;
import com.inqwise.indexer.metadata.TargetPeriod;
import com.inqwise.indexer.metadata.TargetPeriodResolver;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.metadata.UpdatePublicationReadiness;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;

import io.vertx.core.Future;

public class CreateTargetOperation {
	private final DocumentStoreMetadataRepository repository;
	private final TargetDefinitionProvider targetDefinitionProvider;
	private final IndexerProvisioningService provisioningService;
	private final MetadataChangeNotifier metadataChangeNotifier;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();
	private final PublishIndexCommandHandler publishIndex;

	public CreateTargetOperation(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		IndexerQueueResourceManager queueResources,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.targetDefinitionProvider = Objects.requireNonNull(
			targetDefinitionProvider,
			"targetDefinitionProvider"
		);
		this.provisioningService = new IndexerProvisioningService(
			repository,
			indexerDefinitionProvider,
			documentIndexResources,
			queueResources
		);
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
		this.publishIndex = new PublishIndexCommandHandler(repository);
	}

	public Future<TargetRecord> create(CreateTargetCommand create) {
		return resolveDefinition(create)
			.compose(definition -> resolvePeriod(create, definition)
				.compose(period -> failIfTargetExists(definition, period)
					.compose(ignored -> insertTarget(create, definition, period))))
			.compose(target -> create.getCreateIndexer() == null
				? markTargetReady(target)
					.compose(readyTarget -> publishTargetMetadataChanged(readyTarget)
						.map(readyTarget))
				: provisionIndexer(create.getCreateIndexer(), target)
					.compose(indexer -> preparePublication(indexer, create.getCreateIndexer())
						.compose(preparedIndexer -> markTargetReady(target)
							.compose(readyTarget -> publishTargetMetadataChanged(readyTarget)
								.compose(ignored -> publishMetadataChanged(preparedIndexer))
								.map(readyTarget))))
					.recover(error -> markTargetFailed(target).compose(ignored -> Future.failedFuture(error))));
	}

	private Future<TargetDefinition> resolveDefinition(CreateTargetCommand create) {
		return targetDefinitionProvider.getByName(create.getTargetName())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					CommandFailure.stableInvalid(
						"Target definition not found by name: " + create.getTargetName()
					)
				)));
	}

	private Future<TargetPeriod> resolvePeriod(
		CreateTargetCommand create,
		TargetDefinition definition
	) {
		if (definition.periodStrategy() != TargetPeriodStrategy.NONE && create.getTimestamp() == null) {
			return Future.failedFuture(
				"Timestamp is required for target period strategy: " + definition.periodStrategy()
			);
		}

		try {
			return Future.succeededFuture(periodResolver.resolve(
				definition.periodStrategy(),
				create.getTimestamp()
			));
		} catch (RuntimeException error) {
			return Future.failedFuture(error);
		}
	}

	private Future<Void> failIfTargetExists(TargetDefinition definition, TargetPeriod period) {
		return repository.getTargetByDefinitionAndPeriod(new ConcreteTargetKey(
			definition.targetName(),
			period.key()
		)).compose(found -> found
			.map(target -> Future.<Void>failedFuture(
				"Target already exists: " + definition.targetName()
			))
			.orElseGet(() -> Future.succeededFuture()));
	}

	private Future<TargetRecord> insertTarget(
		CreateTargetCommand create,
		TargetDefinition definition,
		TargetPeriod period
	) {
		return repository.insertTarget(new InsertTarget(
			create.getPrefix(),
			definition.targetName(),
			period.key(),
			period.startInclusive(),
			period.endExclusive(),
			TargetStatus.ACTIVE,
			TargetProvisioningState.PROVISIONING
		)).compose(this::getTarget);
	}

	private Future<IndexerRecord> provisionIndexer(
		CreateTargetCommand.CreateIndexer create,
		TargetRecord target
	) {
		return provisioningService.createIndexer(new CreateIndexerProvisioningRequest(
			create.getPrefix(),
			target.id(),
			target.targetName(),
			create.getIndexName(),
			create.getQueueName(),
			create.getIndexerType(),
			create.getRole(),
			create.getIndexOwnership(),
			create.getRuntimeState(),
			create.getPublicationState(),
			create.getMutationState()
		));
	}

	private Future<IndexerRecord> preparePublication(
		IndexerRecord indexer,
		CreateTargetCommand.CreateIndexer create
	) {
		return repository.getPublicationByIndexerId(indexer.id())
			.compose(found -> found
				.map(publication -> markPublicationReady(publication)
					.compose(ignored -> create.getInitialPublicationMode() == InitialPublicationMode.PUBLISH
						? publishIndex.handle(new PublishIndexCommand(indexer.id(), indexer.version()))
							.compose(published -> getIndexer(indexer.id()))
						: Future.succeededFuture(indexer)))
				.orElseGet(() -> Future.failedFuture(
					"Publication not found for indexer: " + indexer.id()
				)));
	}

	private Future<Void> markPublicationReady(PublicationRecord publication) {
		return repository.updatePublicationReadiness(new UpdatePublicationReadiness(
			publication.id(),
			ReadinessState.READY,
			"target creation",
			publication.version()
		));
	}

	private Future<TargetRecord> markTargetReady(TargetRecord target) {
		return repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
			target.id(),
			TargetProvisioningState.READY,
			target.version()
		)).compose(ignored -> getTarget(target.id()));
	}

	private Future<Void> markTargetFailed(TargetRecord target) {
		return repository.getTargetById(target.id())
			.compose(found -> found
				.map(current -> repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
					current.id(),
					TargetProvisioningState.FAILED,
					current.version()
				)))
				.orElseGet(() -> Future.failedFuture("Target not found: " + target.id())));
	}

	private Future<Void> publishMetadataChanged(IndexerRecord indexer) {
		return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
			indexer.id(),
			indexer.targetId(),
			CreateTargetCommand.TYPE,
			indexer.version()
		));
	}

	private Future<Void> publishTargetMetadataChanged(TargetRecord target) {
		return metadataChangeNotifier.targetChanged(new TargetMetadataChanged(
			target.id(),
			target.targetName(),
			target.periodKey(),
			CreateTargetCommand.TYPE,
			target.version()
		));
	}

	private Future<TargetRecord> getTarget(Integer targetId) {
		return repository.getTargetById(targetId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Target not found: " + targetId)));
	}

	private Future<IndexerRecord> getIndexer(Integer indexerId) {
		return repository.getIndexerById(indexerId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexerId)));
	}
}
