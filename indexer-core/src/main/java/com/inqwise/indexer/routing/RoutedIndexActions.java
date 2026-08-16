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
		requirePositive(indexerId, "indexerId");
		requirePositive(targetId, "targetId");
		if (indexerVersion < 0) {
			throw new IllegalArgumentException("indexerVersion must not be negative");
		}
		String queue = Objects.requireNonNull(queueName, "queueName");
		if (queue.isBlank()) {
			throw new IllegalArgumentException("queueName must not be blank");
		}
		actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
		if (actions.isEmpty()) {
			throw new IllegalArgumentException("actions must not be empty");
		}
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
				indexerId,
				targetId,
				Objects.requireNonNull(indexerVersion, "indexerVersion"),
				queueName,
				actions,
				metadataChanged
			);
		}
	}

	private static void requirePositive(Integer value, String name) {
		Integer number = Objects.requireNonNull(value, name);
		if (number <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
