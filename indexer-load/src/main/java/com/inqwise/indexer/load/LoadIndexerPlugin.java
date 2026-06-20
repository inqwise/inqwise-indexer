package com.inqwise.indexer.load;

import java.util.List;
import java.util.Objects;

import com.inqwise.coordination.ExclusiveFlowCoordinator;
import com.inqwise.coordination.LocalExclusiveFlowCoordinator;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.IndexerPlugin;

public class LoadIndexerPlugin implements IndexerPlugin {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final CommandService commandService;
	private final EventPublisher eventPublisher;
	private final ExclusiveFlowCoordinator flowCoordinator;

	public LoadIndexerPlugin(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository
	) {
		this(
			metadataRepository,
			loadRepository,
			null,
			EventPublisher.NOOP,
			new LocalExclusiveFlowCoordinator()
		);
	}

	public LoadIndexerPlugin(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher
	) {
		this(
			metadataRepository,
			loadRepository,
			commandService,
			eventPublisher,
			new LocalExclusiveFlowCoordinator()
		);
	}

	public LoadIndexerPlugin(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher,
		ExclusiveFlowCoordinator flowCoordinator
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.commandService = commandService;
		this.eventPublisher = eventPublisher == null ? EventPublisher.NOOP : eventPublisher;
		this.flowCoordinator = Objects.requireNonNull(flowCoordinator, "flowCoordinator");
	}

	@Override
	public List<IndexerActionReceiveCapability> actionReceiveCapabilities() {
		return List.of(new LoadWriterActionReceiveCapability(
			metadataRepository,
			loadRepository,
			commandService,
			eventPublisher,
			flowCoordinator
		));
	}
}
