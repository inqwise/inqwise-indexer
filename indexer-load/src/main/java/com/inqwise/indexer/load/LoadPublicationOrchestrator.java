package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.commands.CommandService;

import io.vertx.core.Future;

public class LoadPublicationOrchestrator {
	private final CommandService commandService;

	public LoadPublicationOrchestrator(CommandService commandService) {
		this.commandService = Objects.requireNonNull(commandService, "commandService");
	}

	public Future<Void> publishIfReady(IndexerLoadRecord load) {
		if (!isPublishable(load)) {
			return Future.succeededFuture();
		}

		return commandService.submit(new PublishLoadCommand(load.indexerId(), load.version()));
	}

	private boolean isPublishable(IndexerLoadRecord load) {
		if (load.reviewRequired() && load.approvedAt() == null) {
			return false;
		}

		if (load.liveIndexerId() == null) {
			return load.state() == IndexerLoadState.HISTORICAL_COMPLETE
				|| load.state() == IndexerLoadState.APPROVED;
		}

		return load.lastBarrierId() != null
			&& load.lastBarrierReachedAt() != null
			&& (load.state() == IndexerLoadState.CATCH_UP_READY
				|| load.state() == IndexerLoadState.APPROVED);
	}
}
