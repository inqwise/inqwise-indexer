package com.inqwise.indexer.load.commands;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.LoadIndexerReference;
import com.inqwise.indexer.load.repository.LoadPublication;
import com.inqwise.indexer.load.repository.LoadPublicationRepository;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadState;

import java.util.Objects;

import com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;

import io.vertx.core.Future;

public class PublishLoadCommandHandler implements CommandHandler {
	private final LoadPublicationRepository publicationRepository;
	private final IndexerLoadRepository loadRepository;
	private final IndexerLifecycleEventBus eventBus;
	private final CommandService commandService;

	public PublishLoadCommandHandler(
		LoadPublicationRepository publicationRepository,
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus
	) {
		this(publicationRepository, loadRepository, eventBus, null);
	}

	public PublishLoadCommandHandler(
		LoadPublicationRepository publicationRepository,
		IndexerLoadRepository loadRepository,
		IndexerLifecycleEventBus eventBus,
		CommandService commandService
	) {
		this.publicationRepository = Objects.requireNonNull(
			publicationRepository,
			"publicationRepository"
		);
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.eventBus = eventBus == null ? IndexerLifecycleEventBus.NOOP : eventBus;
		this.commandService = commandService;
	}

	@Override
	public String getType() {
		return PublishLoadCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		PublishLoadCommand publish = new PublishLoadCommand(command.toJson());

		return loadRepository.getByIndexerId(publish.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Indexer load not found: " + publish.getIndexerId()
				)))
			.compose(load -> validateLoadVersion(load, publish.getExpectedLoadVersion())
				.compose(valid -> publish(load)));
	}

	private Future<IndexerLoadRecord> validateLoadVersion(IndexerLoadRecord load, long expectedVersion) {
		if (load.version() != expectedVersion) {
			return Future.failedFuture(
				"Indexer load version conflict for id " + load.indexerId() + ": expected "
					+ expectedVersion + " but was " + load.version()
			);
		}
		return Future.succeededFuture(load);
	}

	private Future<Void> publish(IndexerLoadRecord load) {
		return publicationRepository.publish(load)
			.compose(publication -> markPublished(load)
				.compose(ignored -> publishEvents(load, publication))
				.compose(ignored -> cleanup(load, publication)));
	}

	private Future<Void> markPublished(IndexerLoadRecord load) {
		return loadRepository.updateState(new UpdateIndexerLoadState(
			load.indexerId(),
			IndexerLoadState.PUBLISHED,
			load.version()
		));
	}

	private Future<Void> publishEvents(
		IndexerLoadRecord load,
		LoadPublication publication
	) {
		LoadIndexerReference loadIndexer = publication.loadWriter();
		LoadIndexerReference candidate = publication.candidate();
		eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
			candidate.id(),
			candidate.targetId(),
			getType(),
			candidate.version() + 1
		));

		if (load.liveIndexerId() != null && !loadIndexer.id().equals(candidate.id())) {
			eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
				loadIndexer.id(),
				loadIndexer.targetId(),
				getType(),
				loadIndexer.version() + 1
			));
		}

		if (publication.oldPublished() != null) {
			LoadIndexerReference oldPublished = publication.oldPublished();
			eventBus.publishIndexerWakeUp(new IndexerMetadataChanged(
				oldPublished.id(),
				oldPublished.targetId(),
				getType(),
				oldPublished.version() + 1
			));
		}

		return Future.succeededFuture();
	}

	private Future<Void> cleanup(IndexerLoadRecord load, LoadPublication publication) {
		if (commandService == null) {
			return Future.succeededFuture();
		}

		LoadIndexerReference oldPublished = publication.oldPublished();
		return commandService.submit(new CleanupLoadCommand(
			load.indexerId(),
			oldPublished == null ? null : oldPublished.id()
		));
	}
}
