package com.inqwise.indexer.providers;

import java.time.Instant;
import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.TargetRecord;

public record PrepareIndexerForActionsRequest(
	String commandId,
	TargetRecord target,
	IndexerRecord indexer,
	List<IndexerActionItem> actions,
	Instant timestamp
) {
	public PrepareIndexerForActionsRequest {
		actions = actions == null ? List.of() : List.copyOf(actions);
	}
}
