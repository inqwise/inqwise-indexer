package com.inqwise.indexer.hot;

import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.Actions;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteContext;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.metadata.IndexerRecord;

public class MetadataHotIndexer implements HotIndexer {
	private final IndexerRecord record;

	public MetadataHotIndexer(IndexerRecord record) {
		this.record = Objects.requireNonNull(record, "record");
	}

	@Override
	public Integer id() {
		return record.id();
	}

	@Override
	public Integer targetId() {
		return record.targetId();
	}

	@Override
	public String queueName() {
		return record.queueName() == null ? record.indexName() : record.queueName();
	}

	public IndexerRecord record() {
		return record;
	}

	@Override
	public Optional<IndexerActionItem> route(IndexerActionItem item, IndexerActionRouteMode mode) {
		return Actions.getProvider(item.getActionType())
			.router()
			.route(new IndexerActionRouteContext(
				record.targetId(),
				record.id(),
				record.targetName(),
				record.indexName(),
				queueName(),
				record.role()
			), item, mode);
	}
}
