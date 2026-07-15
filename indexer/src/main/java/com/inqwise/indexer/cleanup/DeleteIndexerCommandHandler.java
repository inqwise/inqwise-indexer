package com.inqwise.indexer.cleanup;

import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.catalog.indexers.MarkIndexerDeletingRequest;
import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;

import io.vertx.core.Future;

public final class DeleteIndexerCommandHandler implements CommandHandler {
	private final IndexerOperations indexerOperations;
	private final CommandService commandService;

	public DeleteIndexerCommandHandler(
		IndexerOperations indexerOperations,
		CommandService commandService
	) {
		this.indexerOperations = Objects.requireNonNull(indexerOperations, "indexerOperations");
		this.commandService = Objects.requireNonNull(commandService, "commandService");
	}

	@Override
	public String getType() {
		return DeleteIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		DeleteIndexerCommand delete = new DeleteIndexerCommand(command.toJson());
		if (delete.getExpectedVersion() == null) {
			return Future.failedFuture(
				"Expected version is required for metadata indexer delete: "
					+ delete.getIndexerId()
			);
		}

		return indexerOperations.markDeleting(new MarkIndexerDeletingRequest(
			delete.getIndexerId(),
			delete.getExpectedVersion()
		)).compose(marked -> marked
			.map(indexer -> commandService.submit(new CleanupDeletingIndexerCommand(
				indexer.indexerId()
			)))
			.orElseGet(Future::succeededFuture));
	}
}
