package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.DeleteIndexerCommand;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public class CleanupPublishedLoadCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final CommandService commandService;

	public CleanupPublishedLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		CommandService commandService
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.commandService = Objects.requireNonNull(commandService, "commandService");
	}

	@Override
	public String getType() {
		return CleanupPublishedLoadCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CleanupPublishedLoadCommand cleanup = new CleanupPublishedLoadCommand(command.toJson());

		return loadRepository.getByIndexerId(cleanup.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + cleanup.getIndexerId())))
			.compose(load -> cleanup(load, cleanup.getOldPublishedIndexerId()));
	}

	private Future<Void> cleanup(IndexerLoadRecord load, Integer oldPublishedIndexerId) {
		if (load.state() != IndexerLoadState.PUBLISHED) {
			return Future.failedFuture("Indexer load is not published: " + load.state());
		}

		Future<Void> deleted = deleteIfPresent(oldPublishedIndexerId);
		if (load.liveIndexerId() != null) {
			deleted = deleted.compose(ignored -> deleteIfPresent(load.indexerId()));
		}
		return deleted;
	}

	private Future<Void> deleteIfPresent(Integer indexerId) {
		if (indexerId == null) {
			return Future.succeededFuture();
		}

		return metadataRepository.getIndexerById(indexerId)
			.compose(found -> found
				.map(this::delete)
				.orElseGet(Future::succeededFuture));
	}

	private Future<Void> delete(IndexerRecord indexer) {
		return commandService.submit(new DeleteIndexerCommand(indexer.id(), indexer.version()));
	}
}
