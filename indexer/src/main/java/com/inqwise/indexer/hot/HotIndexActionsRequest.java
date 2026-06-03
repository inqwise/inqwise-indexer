package com.inqwise.indexer.hot;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerActionItem;

public record HotIndexActionsRequest(
	String targetName,
	Instant timestamp,
	List<IndexerActionItem> actions
) {
	public HotIndexActionsRequest {
		actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
	}
}
