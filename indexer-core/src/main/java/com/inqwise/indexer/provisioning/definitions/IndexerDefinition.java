package com.inqwise.indexer.provisioning.definitions;

import java.util.Objects;

public record IndexerDefinition(
	IndexDefinition index,
	QueueDefinition queue
) {
	public IndexerDefinition {
		index = Objects.requireNonNull(index, "index");
		queue = Objects.requireNonNull(queue, "queue");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private IndexDefinition index;
		private QueueDefinition queue;

		private Builder() {
		}

		public Builder withIndex(IndexDefinition value) {
			index = value;
			return this;
		}

		public Builder withQueue(QueueDefinition value) {
			queue = value;
			return this;
		}

		public IndexerDefinition build() {
			return new IndexerDefinition(index, queue);
		}
	}
}
