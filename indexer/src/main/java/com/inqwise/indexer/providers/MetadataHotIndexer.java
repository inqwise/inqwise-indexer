package com.inqwise.indexer.providers;

import java.util.Objects;
import java.util.Optional;

import com.inqwise.indexer.actions.Actions;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteContext;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.metadata.IndexerRecord;

public class MetadataHotIndexer implements HotIndexerCapability {
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
			.route(IndexerActionRouteContext.builder()
				.withTargetId(record.targetId())
				.withIndexerId(record.id())
				.withTargetName(record.targetName())
				.withIndexName(record.indexName())
				.withQueueName(queueName())
				.withRole(record.role())
				.build(), item, mode);
	}
}
