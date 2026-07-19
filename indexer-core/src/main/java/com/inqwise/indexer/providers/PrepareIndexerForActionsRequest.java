package com.inqwise.indexer.providers;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

public record PrepareIndexerForActionsRequest(
	String commandId,
	IndexerModel indexer,
	List<IndexerActionItem> actions,
	Instant timestamp
) {
	public PrepareIndexerForActionsRequest {
		actions = actions == null ? List.of() : List.copyOf(actions);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String commandId;
		private IndexerModel indexer;
		private List<IndexerActionItem> actions;
		private Instant timestamp;

		private Builder() {
		}

		public Builder withCommandId(String value) {
			commandId = value;
			return this;
		}

		public Builder withIndexer(IndexerModel value) {
			indexer = value;
			return this;
		}

		public Builder withActions(List<IndexerActionItem> value) {
			actions = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withTimestamp(Instant value) {
			timestamp = value;
			return this;
		}

		public PrepareIndexerForActionsRequest build() {
			return new PrepareIndexerForActionsRequest(
				Objects.requireNonNull(commandId, "commandId"),
				Objects.requireNonNull(indexer, "indexer"),
				Objects.requireNonNull(actions, "actions"),
				timestamp
			);
		}
	}
}
