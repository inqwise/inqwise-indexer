package com.inqwise.indexer.runtime;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

@FunctionalInterface
public interface IndexerProcessorFactory {
	IndexerProcessor create(
		IndexerModel model,
		IndexerOptions options,
		ActionItemProcessHandler processHandler,
		IndexerEventPublisher eventPublisher
	);
}
