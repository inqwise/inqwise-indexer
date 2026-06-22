package com.inqwise.indexer.load;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.coordination.ExclusiveFlowCoordinator;
import com.inqwise.coordination.LocalExclusiveFlowCoordinator;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerMarkerHandler;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.IndexerRole;
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
	private final IndexerMarkerHandler markerHandler;

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
			new LocalExclusiveFlowCoordinator(),
			IndexerLifecycleEventBus.NOOP
		);
	}

	public LoadIndexerPlugin(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher,
		ExclusiveFlowCoordinator flowCoordinator
	) {
		this(
			metadataRepository,
			loadRepository,
			commandService,
			eventPublisher,
			flowCoordinator,
			IndexerLifecycleEventBus.NOOP
		);
	}

	public LoadIndexerPlugin(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher,
		ExclusiveFlowCoordinator flowCoordinator,
		IndexerLifecycleEventBus lifecycleEventBus
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.commandService = Objects.requireNonNull(commandService, "commandService");
		this.eventPublisher = eventPublisher == null ? EventPublisher.NOOP : eventPublisher;
		this.flowCoordinator = Objects.requireNonNull(flowCoordinator, "flowCoordinator");
		this.markerHandler = new LoadIndexerMarkerHandler(
			loadRepository,
			lifecycleEventBus,
			commandService
		);
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

	@Override
	public Optional<IndexerMarkerHandler> markerHandler(IndexerModel model) {
		Objects.requireNonNull(model, "model");
		if (model.getRole() != IndexerRole.LOAD_WRITER
			&& model.getRole() != IndexerRole.LIVE_WRITER) {
			return Optional.empty();
		}
		return Optional.of(markerHandler);
	}
}
