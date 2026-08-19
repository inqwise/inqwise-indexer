package com.inqwise.indexer.providers;

import java.util.Optional;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteMode;

public interface HotIndexerCapability {
	Integer id();

	Integer targetId();

	long version();

	String queueName();

	Optional<IndexerActionItem> route(IndexerActionItem item, IndexerActionRouteMode mode);
}
