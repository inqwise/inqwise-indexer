package com.inqwise.indexer.load.commands;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.LoadCleanupRepository;
import com.inqwise.indexer.load.repository.MetadataLoadPublicationRepository;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.cleanup.DeleteIndexerCommand;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public final class CleanupLoadCommandHandler implements CommandHandler {
	private final LoadCleanupRepository cleanupRepository;
	private final IndexerLoadRepository loadRepository;
	private final CommandService commandService;

	public CleanupLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		CommandService commandService
	) {
		this.cleanupRepository = new MetadataLoadPublicationRepository(metadataRepository);
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.commandService = Objects.requireNonNull(commandService, "commandService");
	}

	@Override
	public String getType() {
		return CleanupLoadCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CleanupLoadCommand cleanup = new CleanupLoadCommand(command.toJson());

		return loadRepository.getByIndexerId(cleanup.getIndexerId())
			.compose(found -> found
				.map(load -> cleanup(load, cleanup.getOldPublishedIndexerId()))
				.orElseGet(Future::succeededFuture));
	}

	private Future<Void> cleanup(IndexerLoadRecord load, Integer oldPublishedIndexerId) {
		if (load.state() != IndexerLoadState.PUBLISHED
			&& load.state() != IndexerLoadState.CANCELLED) {
			return Future.failedFuture("Indexer load is not cleanup-ready: " + load.state());
		}

		Future<Void> submitted = Future.succeededFuture();
		for (Integer indexerId : removableIndexerIds(load, oldPublishedIndexerId)) {
			submitted = submitted.compose(ignored -> deleteIfPresent(indexerId));
		}

		return submitted.compose(ignored -> loadRepository.finalizeCleanup(
			load.indexerId(),
			load.version()
		));
	}

	private Set<Integer> removableIndexerIds(
		IndexerLoadRecord load,
		Integer oldPublishedIndexerId
	) {
		Set<Integer> indexerIds = new LinkedHashSet<>();
		if (load.state() == IndexerLoadState.PUBLISHED) {
			if (oldPublishedIndexerId != null) {
				indexerIds.add(oldPublishedIndexerId);
			}
			if (load.liveIndexerId() != null) {
				indexerIds.add(load.indexerId());
			}
		} else {
			if (load.liveIndexerId() != null) {
				indexerIds.add(load.liveIndexerId());
			}
			indexerIds.add(load.indexerId());
		}
		return indexerIds;
	}

	private Future<Void> deleteIfPresent(Integer indexerId) {
		return cleanupRepository.getIndexer(indexerId)
			.compose(found -> found
				.map(this::submitDelete)
				.orElseGet(Future::succeededFuture));
	}

	private Future<Void> submitDelete(IndexerRecord indexer) {
		return commandService.submit(new DeleteIndexerCommand(indexer.id(), indexer.version()));
	}
}
