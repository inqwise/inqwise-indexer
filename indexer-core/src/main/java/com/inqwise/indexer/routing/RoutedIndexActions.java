package com.inqwise.indexer.routing;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;

public record RoutedIndexActions(
	Integer indexerId,
	Integer targetId,
	long indexerVersion,
	String queueName,
	List<IndexerActionItem> actions,
	boolean metadataChanged
) {
	public RoutedIndexActions(
		Integer indexerId,
		Integer targetId,
		long indexerVersion,
		String queueName,
		List<IndexerActionItem> actions
	) {
		this(indexerId, targetId, indexerVersion, queueName, actions, false);
	}

	public RoutedIndexActions {
		Objects.requireNonNull(indexerId, "indexerId");
		Objects.requireNonNull(targetId, "targetId");
		actions = List.copyOf(actions);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private Integer indexerId;
		private Integer targetId;
		private Long indexerVersion;
		private String queueName;
		private List<IndexerActionItem> actions;
		private boolean metadataChanged;

		private Builder() {
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexerVersion(long value) {
			indexerVersion = value;
			return this;
		}

		public Builder withQueueName(String value) {
			queueName = value;
			return this;
		}

		public Builder withActions(List<IndexerActionItem> value) {
			actions = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withMetadataChanged(boolean value) {
			metadataChanged = value;
			return this;
		}

		public RoutedIndexActions build() {
			return new RoutedIndexActions(
				Objects.requireNonNull(indexerId, "indexerId"),
				Objects.requireNonNull(targetId, "targetId"),
				Objects.requireNonNull(indexerVersion, "indexerVersion"),
				Objects.requireNonNull(queueName, "queueName"),
				Objects.requireNonNull(actions, "actions"),
				metadataChanged
			);
		}
	}
}
