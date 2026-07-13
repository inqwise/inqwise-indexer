package com.inqwise.indexer.load.catalog;

import java.util.Objects;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.catalog.indexers.CreateIndexerOperation;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.Future;

public final class MetadataLazyLiveWriterCatalog implements LazyLiveWriterCatalog {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final CreateIndexerOperation createIndexer;

	public MetadataLazyLiveWriterCatalog(DocumentStoreMetadataRepository metadataRepository) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.createIndexer = new CreateIndexerOperation(metadataRepository);
	}

	@Override
	public Future<IndexerRecord> getLiveWriter(Integer liveWriterId) {
		return metadataRepository.getIndexerById(liveWriterId)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Linked live writer not found: " + liveWriterId
				)));
	}

	@Override
	public Future<IndexerRecord> createAttachedLiveWriter(
		IndexerLoadRecord load,
		IndexerRecord loadIndexer
	) {
		return createIndexer.create(new InsertIndexer(
			"live" + load.indexerId(),
			load.targetId(),
			loadIndexer.targetName(),
			loadIndexer.indexName(),
			liveQueueName(loadIndexer),
			IndexerType.INDEX,
			IndexerRole.LIVE_WRITER,
			IndexResourceOwnership.ATTACHED,
			IndexerRuntimeState.ACTIVE,
			PublicationState.UNPUBLISHED,
			MutationState.WRITABLE
		));
	}

	private String liveQueueName(IndexerRecord loadIndexer) {
		return loadIndexer.queueName() + "--live";
	}
}
