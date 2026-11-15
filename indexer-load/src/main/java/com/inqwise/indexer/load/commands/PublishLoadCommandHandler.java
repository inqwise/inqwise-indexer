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
		PublishLoadCommand publish = PublishLoadCommand.builder(command.toJson()).build();
		return loadRepository.getByIndexerId(publish.getIndexerId())
			.compose(found -> found
				.map(load -> resume(publish, load))
				.orElseGet(() -> replayCompleted(publish)));
	}

	private Future<Void> replayCompleted(PublishLoadCommand command) {
		return loadRepository.getCompletionByIndexerId(command.getIndexerId())
			.compose(found -> {
				if (found.filter(completion -> completion.terminalState() == IndexerLoadState.PUBLISHED
					&& command.getExpectedLoadVersion() >= 0
					&& command.getExpectedLoadVersion() < Long.MAX_VALUE
					&& completion.terminalVersion() == command.getExpectedLoadVersion() + 1).isPresent()) {
					return Future.succeededFuture();
				}
				return Future.failedFuture("Indexer load not found: " + command.getIndexerId());
			});
	}

	private Future<Void> resume(PublishLoadCommand command, IndexerLoadRecord load) {
		return loadRepository.getPublication(load.indexerId()).compose(found -> {
			if (found.isPresent()) {
				LoadPublication intent = found.orElseThrow();
				if (intent.expectedLoadVersion() != command.getExpectedLoadVersion()
					|| (load.state() == IndexerLoadState.PUBLISHED
						? load.version() != intent.expectedLoadVersion() + 1
						: load.version() != intent.expectedLoadVersion())) {
					return versionConflict(load, command.getExpectedLoadVersion());
				}
				return finish(load, intent);
			}
			if (load.version() != command.getExpectedLoadVersion()) {
				return versionConflict(load, command.getExpectedLoadVersion());
			}
			return publicationRepository.prepare(load)
				.compose(intent -> loadRepository.preparePublication(intent)
					.compose(ignored -> finish(load, intent)));
		});
	}

	private Future<Void> versionConflict(IndexerLoadRecord load, long expectedVersion) {
		return Future.failedFuture(
			"Indexer load version conflict for id " + load.indexerId() + ": expected "
				+ expectedVersion + " but was " + load.version()
		);
	}

	private Future<Void> finish(IndexerLoadRecord load, LoadPublication publication) {
		Future<Void> applied = load.state() == IndexerLoadState.PUBLISHED
			? Future.succeededFuture()
			: publicationRepository.publish(publication).compose(ignored -> markPublished(load));
		return applied.compose(ignored -> publishEvents(load, publication))
			.compose(ignored -> cleanup(load, publication));
	}

	private Future<Void> markPublished(IndexerLoadRecord load) {
		return loadRepository.updateState(UpdateIndexerLoadState.builder()
			.withIndexerId(load.indexerId())
			.withState(IndexerLoadState.PUBLISHED)
			.withExpectedVersion(load.version())
			.build());
	}

	private Future<Void> publishEvents(
		IndexerLoadRecord load,
		LoadPublication publication
	) {
		LoadIndexerReference loadIndexer = publication.loadWriter();
		LoadIndexerReference candidate = publication.candidate();
		eventBus.publishIndexerWakeUp(IndexerMetadataChanged.builder()
			.withIndexerId(candidate.id())
			.withTargetId(candidate.targetId())
			.withCommandType(getType())
			.withVersion(candidate.version() + 1)
			.build());

		if (load.liveIndexerId() != null && !loadIndexer.id().equals(candidate.id())) {
			eventBus.publishIndexerWakeUp(IndexerMetadataChanged.builder()
				.withIndexerId(loadIndexer.id())
				.withTargetId(loadIndexer.targetId())
				.withCommandType(getType())
				.withVersion(loadIndexer.version() + 1)
				.build());
		}

		if (publication.oldPublished() != null) {
			LoadIndexerReference oldPublished = publication.oldPublished();
			eventBus.publishIndexerWakeUp(IndexerMetadataChanged.builder()
				.withIndexerId(oldPublished.id())
				.withTargetId(oldPublished.targetId())
				.withCommandType(getType())
				.withVersion(oldPublished.version() + 1)
				.build());
		}

		return Future.succeededFuture();
	}

	private Future<Void> cleanup(IndexerLoadRecord load, LoadPublication publication) {
		if (commandService == null) {
			return Future.succeededFuture();
		}

		LoadIndexerReference oldPublished = publication.oldPublished();
		return commandService.submit(CleanupLoadCommand.builder()
			.withIndexerId(load.indexerId())
			.withOldPublishedIndexerId(oldPublished == null ? null : oldPublished.id())
			.build());
	}
}
