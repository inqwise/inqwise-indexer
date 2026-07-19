package com.inqwise.indexer.load.adapters.metadata;

import java.util.Objects;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.catalog.LazyLiveWriterCatalog;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.metadata.CreateIndexerOperation;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.MetadataIndexerModels;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;

import io.vertx.core.Future;

public final class MetadataLazyLiveWriterCatalog implements LazyLiveWriterCatalog {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final CreateIndexerOperation createIndexer;

	public MetadataLazyLiveWriterCatalog(DocumentStoreMetadataRepository metadataRepository) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
	}

	@Override
	public Future<IndexerModel> getLiveWriter(Integer liveWriterId) {
		return metadataRepository.getIndexerById(liveWriterId)
			.compose(found -> found
				.map(indexer -> Future.succeededFuture(MetadataIndexerModels.fromRecord(indexer)))
				.orElseGet(() -> Future.failedFuture(
					"Linked live writer not found: " + liveWriterId
				)));
	}

	@Override
	public Future<IndexerModel> createAttachedLiveWriter(
		IndexerLoadRecord load,
		IndexerModel loadIndexer
	) {
		return createIndexer.create(InsertIndexer.builder()
			.withPrefix("live" + load.indexerId())
			.withTargetId(load.targetId())
			.withTargetName(loadIndexer.getTargetName())
			.withIndexName(loadIndexer.getIndexName())
			.withQueueName(liveQueueName(loadIndexer))
			.withType(IndexerType.INDEX)
			.withRole(IndexerRole.LIVE_WRITER)
			.withIndexOwnership(IndexResourceOwnership.ATTACHED)
			.withStatus(IndexerStatus.AVAILABLE)
			.withProvisioningState(IndexerProvisioningState.READY)
			.withRuntimeState(IndexerRuntimeState.ACTIVE)
			.withPublicationState(PublicationState.UNPUBLISHED)
			.withMutationState(MutationState.WRITABLE)
			.build()).map(MetadataIndexerModels::fromRecord);
	}

	private String liveQueueName(IndexerModel loadIndexer) {
		return loadIndexer.getQueueName() + "--live";
	}
}
