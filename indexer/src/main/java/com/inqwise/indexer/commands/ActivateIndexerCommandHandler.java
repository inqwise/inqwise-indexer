package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.IndexerRepository;
import com.inqwise.indexer.IndexerStatus;

import io.vertx.core.Future;

public class ActivateIndexerCommandHandler implements CommandHandler {
	private final IndexerRepository repository;
	private final IndexerLifecycleEventBus eventBus;

	public ActivateIndexerCommandHandler(
		IndexerRepository repository,
		IndexerLifecycleEventBus eventBus
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	@Override
	public String getType() {
		return ActivateIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		ActivateIndexerCommand activate = new ActivateIndexerCommand(command.toJson());

		return repository.get(activate.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.failedFuture("Indexer not found: " + activate.getIndexerId());
				}

				IndexerModel model = found.get();
				if (model.getStatus() == IndexerStatus.DELETED) {
					return Future.failedFuture("Cannot activate deleted indexer: " + activate.getIndexerId());
				}

				if (model.getStatus().isActive()) {
					return publish(model, activate.getCommandId());
				}

				return repository.updateStatus(activate.getIndexerId(), IndexerStatus.STARTED)
					.compose(updated -> updated
						.map(value -> publish(value, activate.getCommandId()))
						.orElseGet(() -> Future.failedFuture(
							"Indexer not found: " + activate.getIndexerId()
						)));
			});
	}

	private Future<Void> publish(IndexerModel model, String commandId) {
		return eventBus.publish(new IndexerLifecycleChanged(
			model.getId(),
			model.getStatus(),
			model.getVersion(),
			commandId
		));
	}
}
