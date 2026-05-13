package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.IndexerRepository;
import com.inqwise.indexer.IndexerStatus;

import io.vertx.core.Future;

public class DeactivateIndexerCommandHandler implements CommandHandler {
	private final IndexerRepository repository;
	private final IndexerLifecycleEventBus eventBus;

	public DeactivateIndexerCommandHandler(
		IndexerRepository repository,
		IndexerLifecycleEventBus eventBus
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	@Override
	public String getType() {
		return DeactivateIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		DeactivateIndexerCommand deactivate = new DeactivateIndexerCommand(command.toJson());

		return repository.get(deactivate.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.failedFuture("Indexer not found: " + deactivate.getIndexerId());
				}

				IndexerModel model = found.get();
				if (model.getStatus() == IndexerStatus.DELETED) {
					return publish(model);
				}

				return repository.updateStatus(deactivate.getIndexerId(), IndexerStatus.NON_ACTIVE)
					.compose(updated -> updated
						.map(this::publish)
						.orElseGet(() -> Future.failedFuture(
							"Indexer not found: " + deactivate.getIndexerId()
						)));
			});
	}

	private Future<Void> publish(IndexerModel model) {
		return eventBus.publish(new IndexerLifecycleChanged(
			model.getId(),
			getType(),
			model.getVersion()
		));
	}
}
