package com.inqwise.indexer.load.adapters.metadata;

import java.util.Objects;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.LoadRequest;
import com.inqwise.indexer.load.catalog.LoadCreatedIndexer;
import com.inqwise.indexer.load.catalog.LoadCreationCatalog;
import com.inqwise.indexer.load.catalog.LoadCreationTarget;
import com.inqwise.indexer.load.catalog.LoadStartContext;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.metadata.CreateIndexerOperation;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.provisioning.GeneratedIndexerResources;
import com.inqwise.indexer.provisioning.IndexerResourceNameGenerator;

import io.vertx.core.Future;

public final class MetadataLoadCreationCatalog implements LoadCreationCatalog {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final CreateIndexerOperation createIndexer;

	public MetadataLoadCreationCatalog(DocumentStoreMetadataRepository metadataRepository) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
	}

	@Override
	public Future<LoadCreationTarget> getReadyTarget(Integer targetId) {
		return metadataRepository.getTargetById(targetId)
			.compose(found -> found
				.map(this::validateTargetReady)
				.orElseGet(() -> Future.failedFuture(
					"Target not found for load creation: " + targetId
				)));
	}

	@Override
	public Future<LoadCreatedIndexer> createLoadWriter(LoadCreationTarget target) {
		GeneratedIndexerResources resources = IndexerResourceNameGenerator.forTarget(
			target.targetName()
		);
		return createIndexer.create(InsertIndexer.builder()
			.withPrefix(resources.prefix())
			.withTargetId(target.id())
			.withTargetName(target.targetName())
			.withIndexName(resources.indexName())
			.withQueueName(resources.queueName())
			.withType(IndexerType.INDEX)
			.withRole(IndexerRole.LOAD_WRITER)
			.withIndexOwnership(IndexResourceOwnership.OWNER)
			.withStatus(IndexerStatus.AVAILABLE)
			.withProvisioningState(IndexerProvisioningState.READY)
			.withRuntimeState(IndexerRuntimeState.ACTIVE)
			.withPublicationState(PublicationState.UNPUBLISHED)
			.withMutationState(MutationState.WRITABLE)
			.build()).map(this::creationIndexer);
	}

	@Override
	public Future<LoadCreatedIndexer> createImmediateLiveWriter(
		LoadCreationTarget target,
		LoadCreatedIndexer loadWriter
	) {
		return createIndexer.create(InsertIndexer.builder()
			.withPrefix(loadWriter.prefix())
			.withTargetId(target.id())
			.withTargetName(target.targetName())
			.withIndexName(loadWriter.indexName())
			.withQueueName(loadWriter.queueName() + "--live")
			.withType(IndexerType.INDEX)
			.withRole(IndexerRole.LIVE_WRITER)
			.withIndexOwnership(IndexResourceOwnership.ATTACHED)
			.withStatus(IndexerStatus.AVAILABLE)
			.withProvisioningState(IndexerProvisioningState.READY)
			.withRuntimeState(IndexerRuntimeState.ACTIVE)
			.withPublicationState(PublicationState.UNPUBLISHED)
			.withMutationState(MutationState.WRITABLE)
			.build()).map(this::creationIndexer);
	}

	@Override
	public Future<LoadStartContext> prepareStart(IndexerLoadRecord load) {
		return getLoadWriter(load.indexerId())
			.compose(loadWriter -> getTarget(load.targetId())
				.compose(target -> verifyLiveWriter(load)
					.map(ignored -> LoadStartContext.builder()
						.withRequest(loadRequest(load, loadWriter, target))
						.withIndexName(loadWriter.indexName())
						.withQueueName(loadWriter.queueName())
						.build())));
	}

	private Future<LoadCreationTarget> validateTargetReady(TargetRecord target) {
		if (target.status() != TargetStatus.ACTIVE) {
			return Future.failedFuture("Target is not active: " + target.id());
		}
		if (target.provisioningState() != TargetProvisioningState.READY) {
			return Future.failedFuture(
				"Target provisioning is not ready: " + target.id() + " state "
					+ target.provisioningState()
			);
		}
		return Future.succeededFuture(LoadCreationTarget.builder()
			.withId(target.id())
			.withTargetName(target.targetName())
			.build());
	}

	private LoadCreatedIndexer creationIndexer(IndexerRecord indexer) {
		return LoadCreatedIndexer.builder()
			.withId(indexer.id())
			.withTargetId(indexer.targetId())
			.withPrefix(indexer.prefix())
			.withIndexName(indexer.indexName())
			.withQueueName(indexer.queueName())
			.withVersion(indexer.version())
			.build();
	}

	private Future<IndexerRecord> getLoadWriter(Integer indexerId) {
		return metadataRepository.getIndexerById(indexerId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Load writer not found: " + indexerId)));
	}

	private Future<TargetRecord> getTarget(Integer targetId) {
		return metadataRepository.getTargetById(targetId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Target not found: " + targetId)));
	}

	private Future<Void> verifyLiveWriter(IndexerLoadRecord load) {
		if (load.liveIndexerId() == null) {
			return Future.succeededFuture();
		}
		return metadataRepository.getIndexerById(load.liveIndexerId())
			.compose(found -> found
				.map(ignored -> Future.<Void>succeededFuture())
				.orElseGet(() -> Future.failedFuture(
					"Live writer not found: " + load.liveIndexerId()
				)));
	}

	private LoadRequest loadRequest(
		IndexerLoadRecord load,
		IndexerRecord loadWriter,
		TargetRecord target
	) {
		return LoadRequest.builder()
			.withIndexerId(load.indexerId())
			.withTargetId(load.targetId())
			.withLiveIndexerId(load.liveIndexerId())
			.withProviderId(load.providerId())
			.withTargetName(target.targetName())
			.withIndexName(loadWriter.indexName())
			.withQueueName(loadWriter.queueName())
			.withReloadStartAt(load.reloadStartAt())
			.withLiveReplayFrom(load.liveReplayFrom())
			.withSourceFrom(load.sourceFrom())
			.withSourceTo(load.sourceTo())
			.withSourceQuery(load.sourceQuery())
			.withSourcePlaybookId(load.sourcePlaybookId())
			.build();
	}
}
