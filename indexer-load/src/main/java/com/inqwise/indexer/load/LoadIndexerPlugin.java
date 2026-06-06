package com.inqwise.indexer.load;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.IndexerPlugin;

public class LoadIndexerPlugin implements IndexerPlugin {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;

	public LoadIndexerPlugin(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
	}

	@Override
	public List<IndexerActionReceiveCapability> actionReceiveCapabilities() {
		return List.of(new LoadWriterActionReceiveCapability(
			metadataRepository,
			loadRepository
		));
	}
}
