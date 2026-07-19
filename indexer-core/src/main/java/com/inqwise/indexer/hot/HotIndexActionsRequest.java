package com.inqwise.indexer.hot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;

public record HotIndexActionsRequest(
	String targetName,
	Instant timestamp,
	List<IndexerActionItem> actions
) {
	public HotIndexActionsRequest {
		actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String targetName;
		private Instant timestamp;
		private List<IndexerActionItem> actions;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withTimestamp(Instant value) {
			timestamp = value;
			return this;
		}

		public Builder withActions(List<IndexerActionItem> value) {
			actions = value == null ? null : List.copyOf(value);
			return this;
		}

		public HotIndexActionsRequest build() {
			return new HotIndexActionsRequest(
				targetName,
				timestamp,
				Objects.requireNonNull(actions, "actions")
			);
		}
	}
}
