package com.inqwise.indexer.metadata;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

public final class MetadataIndexerModels {
	private MetadataIndexerModels() {
	}

	public static IndexerModel fromRecord(IndexerRecord indexer) {
		Objects.requireNonNull(indexer, "indexer");
		return IndexerModel.builder()
			.withId(indexer.id())
			.withUid(indexer.uid())
			.withTargetId(indexer.targetId())
			.withTargetName(indexer.targetName())
			.withIndexName(indexer.indexName())
			.withQueueName(indexer.queueName())
			.withType(indexer.type())
			.withRole(indexer.role())
			.withIndexOwnership(indexer.indexOwnership())
			.withRuntimeState(indexer.runtimeState())
			.withVersion(indexer.version())
			.build();
	}
}
