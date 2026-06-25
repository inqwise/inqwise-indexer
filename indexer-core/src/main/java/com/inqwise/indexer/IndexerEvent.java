package com.inqwise.indexer;

import java.util.Objects;

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
}
