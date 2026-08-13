package com.inqwise.indexer.node;

import com.inqwise.indexer.providers.IndexerPlugins;

@FunctionalInterface
public interface IndexerPluginFactory {
	IndexerPluginFactory NONE = context -> IndexerPlugins.empty();

	IndexerPlugins create(IndexerPluginContext context);
}
