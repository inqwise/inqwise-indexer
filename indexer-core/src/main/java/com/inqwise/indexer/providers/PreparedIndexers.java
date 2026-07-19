package com.inqwise.indexer.providers;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

public record PreparedIndexers(
	List<IndexerModel> indexers,
	boolean metadataChanged
) {
	public PreparedIndexers {
		indexers = indexers == null ? List.of() : List.copyOf(indexers);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<IndexerModel> indexers = List.of();
		private Boolean metadataChanged;

		private Builder() {
		}

		public Builder withIndexers(List<IndexerModel> value) {
			indexers = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public Builder withMetadataChanged(boolean value) {
			metadataChanged = value;
			return this;
		}

		public PreparedIndexers build() {
			return new PreparedIndexers(
				indexers,
				Objects.requireNonNull(metadataChanged, "metadataChanged")
			);
		}
	}
}
