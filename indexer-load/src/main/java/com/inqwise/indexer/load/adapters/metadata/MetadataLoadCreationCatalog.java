package com.inqwise.indexer.load.adapters.metadata;

import java.util.Objects;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.LoadRequest;
import com.inqwise.indexer.load.catalog.LoadCreatedIndexer;
import com.inqwise.indexer.load.catalog.LoadCreationCatalog;
import com.inqwise.indexer.load.catalog.LoadCreationTarget;
import com.inqwise.indexer.load.catalog.LoadStartContext;
import com.inqwise.indexer.catalog.indexers.CreateIndexerOperation;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
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
import io.vertx.core.json.JsonObject;

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
		return createIndexer.create(new InsertIndexer(
			resources.prefix(),
			target.id(),
			target.targetName(),
			resources.indexName(),
			resources.queueName(),
			IndexerType.INDEX,
			IndexerRole.LOAD_WRITER,
			IndexResourceOwnership.OWNER,
			IndexerRuntimeState.ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		)).map(this::creationIndexer);
	}

	@Override
	public Future<LoadCreatedIndexer> createImmediateLiveWriter(
		LoadCreationTarget target,
		LoadCreatedIndexer loadWriter
	) {
		return createIndexer.create(new InsertIndexer(
			loadWriter.prefix(),
			target.id(),
			target.targetName(),
			loadWriter.indexName(),
			loadWriter.queueName() + "--live",
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.ATTACHED,
			IndexerRuntimeState.ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		)).map(this::creationIndexer);
	}

	@Override
	public Future<LoadStartContext> prepareStart(IndexerLoadRecord load) {
		return getLoadWriter(load.indexerId())
			.compose(loadWriter -> getTarget(load.targetId())
				.compose(target -> verifyLiveWriter(load)
					.map(ignored -> new LoadStartContext(
						loadRequest(load, loadWriter, target),
						loadWriter.indexName(),
						loadWriter.queueName()
					))));
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
		return Future.succeededFuture(new LoadCreationTarget(target.id(), target.targetName()));
	}

	private LoadCreatedIndexer creationIndexer(IndexerRecord indexer) {
		return new LoadCreatedIndexer(
			indexer.id(),
			indexer.targetId(),
			indexer.prefix(),
			indexer.indexName(),
			indexer.queueName(),
			indexer.version()
		);
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
		return new LoadRequest(
			load.indexerId(),
			load.targetId(),
			load.liveIndexerId(),
			load.providerId(),
			target.targetName(),
			loadWriter.indexName(),
			loadWriter.queueName(),
			load.reloadStartAt(),
			load.liveReplayFrom(),
			load.sourceFrom(),
			load.sourceTo(),
			copy(load.sourceQuery()),
			load.sourcePlaybookId()
		);
	}

	private JsonObject copy(JsonObject json) {
		return json == null ? null : json.copy();
	}
}
