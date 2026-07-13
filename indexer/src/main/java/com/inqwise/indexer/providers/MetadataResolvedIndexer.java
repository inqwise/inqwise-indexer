package com.inqwise.indexer.providers;

import java.util.Optional;

import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.hot.HotIndexer;
import com.inqwise.indexer.hot.MetadataHotIndexer;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;

public record MetadataResolvedIndexer(
	IndexerRecord record,
	Optional<HotIndexer> hotIndexer
) implements ResolvedIndexer {
	public MetadataResolvedIndexer(IndexerRecord record) {
		this(record, toHotIndexer(record));
	}

	public MetadataResolvedIndexer {
		hotIndexer = hotIndexer == null ? Optional.empty() : hotIndexer;
	}

	private static Optional<HotIndexer> toHotIndexer(IndexerRecord record) {
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
