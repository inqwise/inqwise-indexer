package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.lifecycle.TargetMetadataChanged;
import com.inqwise.indexer.commands.InitialPublicationMode;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.metadata.PublicationRecord;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;
import com.inqwise.indexer.publication.IndexPublicationService;
import com.inqwise.indexer.publication.MarkIndexReadyRequest;
import com.inqwise.indexer.publication.PublicationReadinessResult;
import com.inqwise.indexer.publication.PublishIndexRequest;
import com.inqwise.indexer.provisioning.CreateIndexerProvisioningRequest;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;
import com.inqwise.indexer.provisioning.ProvisionedIndexer;

import io.vertx.core.Future;

public class MetadataTargetManagementService implements TargetManagementService {
	private static final String CHANGE_TYPE = "target.create";
	private static final String RECOVERY_CHANGE_TYPE = "target.provisioning.recover";
	private final DocumentStoreMetadataRepository repository;
	private final TargetDefinitionProvider targetDefinitionProvider;
	private final IndexerProvisioningService provisioningService;
	private final MetadataChangeNotifier metadataChangeNotifier;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();
	private final IndexPublicationService publicationService;

	public MetadataTargetManagementService(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerProvisioningService provisioningService,
		IndexPublicationService publicationService,
		MetadataChangeNotifier metadataChangeNotifier
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.targetDefinitionProvider = Objects.requireNonNull(
			targetDefinitionProvider,
			"targetDefinitionProvider"
		);
		this.provisioningService = Objects.requireNonNull(
			provisioningService,
			"provisioningService"
		);
		this.metadataChangeNotifier = Objects.requireNonNull(
			metadataChangeNotifier,
			"metadataChangeNotifier"
		);
		this.publicationService = Objects.requireNonNull(
			publicationService,
			"publicationService"
		);
	}

	@Override
	public Future<TargetManagementResult> createTarget(CreateTargetRequest create) {
		return resolveDefinition(create)
			.compose(definition -> resolvePeriod(create, definition)
				.compose(period -> failIfTargetExists(definition, period)
					.compose(ignored -> insertTarget(create, definition, period))))
			.compose(target -> create.createIndexer() == null
				? markTargetReady(target)
					.compose(readyTarget -> publishTargetMetadataChanged(readyTarget)
						.map(readyTarget))
				: provisionIndexer(create.createIndexer(), target)
					.compose(indexer -> preparePublication(indexer, create.createIndexer())
						.compose(preparedIndexer -> markTargetReady(target)
							.compose(readyTarget -> publishTargetMetadataChanged(readyTarget)
								.compose(ignored -> publishMetadataChanged(preparedIndexer))
								.map(readyTarget))))
					.recover(error -> markTargetFailed(target).compose(ignored -> Future.failedFuture(error))))
			.map(this::toManagementResult);
	}

	@Override
	public Future<TargetManagementResult> recoverProvisioning(RecoverTargetProvisioningRequest request) {
		Objects.requireNonNull(request, "request");
		return getTarget(request.targetId()).compose(target -> {
			if (alreadyRecovered(target, request)) {
				return publishTargetMetadataChanged(target, RECOVERY_CHANGE_TYPE).map(target);
			}
			if (target.version() != request.expectedVersion()) {
				return Future.failedFuture(
					"Target version conflict for id " + target.id() + ": expected "
						+ request.expectedVersion() + " but was " + target.version()
				);
			}
			if (target.status() != TargetStatus.ACTIVE) {
				return Future.failedFuture("Target is not active: " + target.id());
			}
			if (target.provisioningState() != TargetProvisioningState.FAILED) {
				return Future.failedFuture("Target provisioning is not failed: " + target.id());
			}
			return repository.updateTargetProvisioningState(new UpdateTargetProvisioningState(
				target.id(),
				TargetProvisioningState.READY,
				request.expectedVersion()
			)).compose(ignored -> publishTargetMetadataChanged(
				target,
				RECOVERY_CHANGE_TYPE,
				request.expectedVersion() + 1L
			)).compose(ignored -> getTarget(target.id()));
		}).map(this::toManagementResult);
	}

	private TargetManagementResult toManagementResult(TargetRecord target) {
		return new TargetManagementResult(
			target.id(),
			target.targetName(),
			target.status(),
			target.provisioningState(),
			target.version()
		);
	}

	private boolean alreadyRecovered(
		TargetRecord target,
		RecoverTargetProvisioningRequest request
	) {
		return request.expectedVersion() >= 0L
			&& request.expectedVersion() < Long.MAX_VALUE
			&& target.version() == request.expectedVersion() + 1L
			&& target.status() == TargetStatus.ACTIVE
			&& target.provisioningState() == TargetProvisioningState.READY;
	}

	private Future<TargetDefinition> resolveDefinition(CreateTargetRequest create) {
		return targetDefinitionProvider.getByName(create.targetName())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					CommandFailure.stableInvalid(
						"Target definition not found by name: " + create.targetName()
					)
				)));
	}

	private Future<TargetPeriod> resolvePeriod(
		CreateTargetRequest create,
		TargetDefinition definition
	) {
		if (definition.periodStrategy() != TargetPeriodStrategy.NONE && create.timestamp() == null) {
			return Future.failedFuture(
				"Timestamp is required for target period strategy: " + definition.periodStrategy()
			);
		}

		try {
			return Future.succeededFuture(periodResolver.resolve(
				definition.periodStrategy(),
				create.timestamp()
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
		CreateTargetRequest create,
		TargetDefinition definition,
		TargetPeriod period
	) {
		return repository.insertTarget(new InsertTarget(
			create.prefix(),
			definition.targetName(),
			period.key(),
			period.startInclusive(),
			period.endExclusive(),
			TargetStatus.ACTIVE,
			TargetProvisioningState.PROVISIONING
		)).compose(this::getTarget);
	}

	private Future<ProvisionedIndexer> provisionIndexer(
		CreateTargetIndexerRequest create,
		TargetRecord target
	) {
		return provisioningService.createIndexer(new CreateIndexerProvisioningRequest(
			create.prefix(),
			target.id(),
			target.targetName(),
			create.indexName(),
			create.queueName(),
			create.indexerType(),
			create.role(),
			create.indexOwnership(),
			create.runtimeState(),
			create.publicationState(),
			create.mutationState()
		));
	}

	private Future<ProvisionedIndexer> preparePublication(
		ProvisionedIndexer indexer,
		CreateTargetIndexerRequest create
	) {
		return repository.getPublicationByIndexerId(indexer.indexerId())
			.compose(found -> found
				.map(publication -> markPublicationReady(publication)
					.compose(ignored -> create.initialPublicationMode() == InitialPublicationMode.PUBLISH
						? publicationService.publish(new PublishIndexRequest(
							indexer.indexerId(),
							indexer.version()
						)).map(published -> new ProvisionedIndexer(
							published.indexerId(),
							published.targetId(),
							published.version()
						))
						: Future.succeededFuture(indexer)))
				.orElseGet(() -> Future.failedFuture(
					"Publication not found for indexer: " + indexer.indexerId()
				)));
	}

	private Future<PublicationReadinessResult> markPublicationReady(PublicationRecord publication) {
		return publicationService.markReady(new MarkIndexReadyRequest(
			publication.id(),
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

	private Future<Void> publishMetadataChanged(ProvisionedIndexer indexer) {
		return metadataChangeNotifier.indexerChanged(new IndexerMetadataChanged(
			indexer.indexerId(),
			indexer.targetId(),
			CHANGE_TYPE,
			indexer.version()
		));
	}

	private Future<Void> publishTargetMetadataChanged(TargetRecord target) {
		return publishTargetMetadataChanged(target, CHANGE_TYPE);
	}

	private Future<Void> publishTargetMetadataChanged(TargetRecord target, String changeType) {
		return publishTargetMetadataChanged(target, changeType, target.version());
	}

	private Future<Void> publishTargetMetadataChanged(
		TargetRecord target,
		String changeType,
		long version
	) {
		return metadataChangeNotifier.targetChanged(new TargetMetadataChanged(
			target.id(),
			target.targetName(),
			target.periodKey(),
			changeType,
			version
		));
	}

	private Future<TargetRecord> getTarget(Integer targetId) {
		return repository.getTargetById(targetId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Target not found: " + targetId)));
	}
}
