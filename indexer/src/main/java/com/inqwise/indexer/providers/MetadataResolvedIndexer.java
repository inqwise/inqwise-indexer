package com.inqwise.indexer.providers;

import java.util.Objects;
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

	public static Builder builder() {
		return new Builder();
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

	public static final class Builder {
		private IndexerModel model;
		private Optional<HotIndexerCapability> hotIndexer = Optional.empty();

		private Builder() {
		}

		public Builder withRecord(IndexerRecord value) {
			IndexerRecord record = Objects.requireNonNull(value, "record");
			model = MetadataIndexerModels.fromRecord(record);
			hotIndexer = toHotIndexer(record);
			return this;
		}

		public Builder withModel(IndexerModel value) {
			model = value;
			return this;
		}

		public Builder withHotIndexer(HotIndexerCapability value) {
			hotIndexer = Optional.ofNullable(value);
			return this;
		}

		public MetadataResolvedIndexer build() {
			return new MetadataResolvedIndexer(
				Objects.requireNonNull(model, "model"),
				hotIndexer
			);
		}
	}
}
