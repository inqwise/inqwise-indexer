package com.inqwise.indexer.hot;

import java.util.Optional;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteMode;

public interface HotIndexer {
	Integer id();

	Integer targetId();

	String queueName();

	Optional<IndexerActionItem> route(IndexerActionItem item, IndexerActionRouteMode mode);
}
