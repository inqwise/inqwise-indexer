package com.inqwise.indexer;

@FunctionalInterface
public interface IndexerProcessorFactory {
	IndexerProcessor create(
		IndexerModel model,
		IndexerOptions options,
		ActionItemProcessHandler processHandler,
		IndexerEventPublisher eventPublisher
	);
}
