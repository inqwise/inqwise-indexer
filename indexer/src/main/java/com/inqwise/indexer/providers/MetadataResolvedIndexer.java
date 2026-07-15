package com.inqwise.indexer.providers;

import java.util.Optional;

import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.metadata.MetadataIndexerModels;

public record MetadataResolvedIndexer(
	IndexerModel model,
	Optional<HotIndexerCapability> hotIndexer
) implements ResolvedIndexer {
	public MetadataResolvedIndexer(IndexerRecord record) {
		this(MetadataIndexerModels.fromRecord(record), toHotIndexer(record));
	}

	public MetadataResolvedIndexer {
		hotIndexer = hotIndexer == null ? Optional.empty() : hotIndexer;
	}

	private static Optional<HotIndexerCapability> toHotIndexer(IndexerRecord record) {
		if (record.role() != IndexerRole.LIVE_WRITER
			|| record.status() != IndexerStatus.AVAILABLE
			|| record.provisioningState() != IndexerProvisioningState.READY
			|| record.runtimeState() != IndexerRuntimeState.ACTIVE
			|| record.mutationState() != MutationState.WRITABLE) {
			return Optional.empty();
		}

		return Optional.of(new MetadataHotIndexer(record));
	}
}
