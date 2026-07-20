package com.inqwise.indexer.runtime;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.actions.IndexerActionItem;

public class IndexerEvent {
	private final IndexerEventType type;
	private final IndexerModel model;
	private final IndexerActionItem item;
	private final Throwable error;

	public IndexerEvent(IndexerEventType type, IndexerModel model, IndexerActionItem item, Throwable error) {
		this.type = Objects.requireNonNull(type, "type");
		this.model = Objects.requireNonNull(model, "model");
		this.item = item;
		this.error = error;
	}

	public static Builder builder() {
		return new Builder();
	}

	public IndexerEventType getType() {
		return type;
	}

	public IndexerModel getModel() {
		return model;
	}

	public IndexerActionItem getItem() {
		return item;
	}

	public Throwable getError() {
		return error;
	}

	public static final class Builder {
		private IndexerEventType type;
		private IndexerModel model;
		private IndexerActionItem item;
		private Throwable error;

		public Builder withType(IndexerEventType value) {
			type = value;
			return this;
		}

		public Builder withModel(IndexerModel value) {
			model = value;
			return this;
		}

		public Builder withItem(IndexerActionItem value) {
			item = value;
			return this;
		}

		public Builder withError(Throwable value) {
			error = value;
			return this;
		}

		public IndexerEvent build() {
			return new IndexerEvent(
				Objects.requireNonNull(type, "type"),
				Objects.requireNonNull(model, "model"),
				item,
				error
			);
		}
	}
}
