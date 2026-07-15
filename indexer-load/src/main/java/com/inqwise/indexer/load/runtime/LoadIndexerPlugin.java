package com.inqwise.indexer.load.runtime;

import com.inqwise.indexer.load.catalog.LazyLiveWriterCatalog;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.inqwise.coordination.ExclusiveFlowCoordinator;
import com.inqwise.coordination.LocalExclusiveFlowCoordinator;
import com.inqwise.events.EventPublisher;
import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.providers.IndexerMarkerHandler;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.IndexerPlugin;

public class LoadIndexerPlugin implements IndexerPlugin {
	private final LazyLiveWriterCatalog lazyLiveWriterCatalog;
	private final IndexerLoadRepository loadRepository;
	private final CommandService commandService;
	private final EventPublisher eventPublisher;
	private final ExclusiveFlowCoordinator flowCoordinator;
	private final IndexerMarkerHandler markerHandler;

	public LoadIndexerPlugin(
		LazyLiveWriterCatalog lazyLiveWriterCatalog,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher
	) {
		this(
			lazyLiveWriterCatalog,
			loadRepository,
			commandService,
			eventPublisher,
			new LocalExclusiveFlowCoordinator(),
			IndexerLifecycleEventBus.NOOP
		);
	}

	public LoadIndexerPlugin(
		LazyLiveWriterCatalog lazyLiveWriterCatalog,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher,
		ExclusiveFlowCoordinator flowCoordinator
	) {
		this(
			lazyLiveWriterCatalog,
			loadRepository,
			commandService,
			eventPublisher,
			flowCoordinator,
			IndexerLifecycleEventBus.NOOP
		);
	}

	public LoadIndexerPlugin(
		LazyLiveWriterCatalog lazyLiveWriterCatalog,
		IndexerLoadRepository loadRepository,
		CommandService commandService,
		EventPublisher eventPublisher,
		ExclusiveFlowCoordinator flowCoordinator,
		IndexerLifecycleEventBus lifecycleEventBus
	) {
		this.lazyLiveWriterCatalog = Objects.requireNonNull(
			lazyLiveWriterCatalog,
			"lazyLiveWriterCatalog"
		);
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
			lazyLiveWriterCatalog,
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
