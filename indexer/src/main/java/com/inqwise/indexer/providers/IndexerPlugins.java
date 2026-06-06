package com.inqwise.indexer.providers;

import java.util.List;
import java.util.Objects;

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
}
