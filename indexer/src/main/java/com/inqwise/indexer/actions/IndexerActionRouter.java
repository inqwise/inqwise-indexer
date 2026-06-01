package com.inqwise.indexer.actions;

import java.util.Optional;

import com.inqwise.indexer.IndexerActionItem;

public interface IndexerActionRouter {
	Optional<IndexerActionItem> route(
		IndexerActionRouteContext context,
		IndexerActionItem item,
		IndexerActionRouteMode mode
	);
}
