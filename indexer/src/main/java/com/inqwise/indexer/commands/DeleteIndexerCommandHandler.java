package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.operations.IndexerOperations;
import com.inqwise.indexer.operations.MarkIndexerDeletingRequest;

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
				indexer.id(),
				indexer.version()
			)))
			.orElseGet(Future::succeededFuture));
	}
}
