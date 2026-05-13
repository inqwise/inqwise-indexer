package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexerLifecycleChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.IndexerRepository;
import com.inqwise.indexer.IndexerStatus;

import io.vertx.core.Future;

public class DeleteIndexerCommandHandler implements CommandHandler {
	private final IndexerRepository repository;
	private final IndexerLifecycleEventBus eventBus;

	public DeleteIndexerCommandHandler(
		IndexerRepository repository,
		IndexerLifecycleEventBus eventBus
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
	}

	@Override
	public String getType() {
		return DeleteIndexerCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		DeleteIndexerCommand delete = new DeleteIndexerCommand(command.toJson());

		return repository.get(delete.getIndexerId())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.succeededFuture();
				}

				IndexerModel model = found.get();
				if (model.getStatus() == IndexerStatus.DELETED) {
					return publish(model);
				}

				return repository.updateStatus(delete.getIndexerId(), IndexerStatus.DELETED)
					.compose(updated -> updated
						.map(this::publish)
						.orElseGet(() -> Future.succeededFuture()));
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
