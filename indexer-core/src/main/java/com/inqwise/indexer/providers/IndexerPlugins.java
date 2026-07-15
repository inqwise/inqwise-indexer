package com.inqwise.indexer.providers;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerModel;

public final class IndexerPlugins {
	private final List<IndexerPlugin> plugins;

	public IndexerPlugins(List<IndexerPlugin> plugins) {
		this.plugins = List.copyOf(Objects.requireNonNull(plugins, "plugins"));
	}

	public static IndexerPlugins empty() {
		return new IndexerPlugins(List.of());
	}

	public List<IndexerActionReceiveCapability> actionReceiveCapabilities() {
		return plugins.stream()
			.flatMap(plugin -> plugin.actionReceiveCapabilities().stream())
			.toList();
	}

	public IndexerMarkerHandler markerHandler(IndexerModel model) {
		Objects.requireNonNull(model, "model");
		List<IndexerMarkerHandler> handlers = plugins.stream()
			.map(plugin -> plugin.markerHandler(model))
			.flatMap(java.util.Optional::stream)
			.toList();
		if (handlers.size() > 1) {
			throw new IllegalStateException(
				"Multiple marker handlers configured for indexer: " + model.getId()
			);
		}
		return handlers.isEmpty() ? IndexerMarkerHandler.FAILING : handlers.get(0);
	}
}
