package com.inqwise.indexer.commands;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.IndexerLifecycleChanged;
import com.inqwise.indexer.IndexerLifecycleEventBus;
import com.inqwise.indexer.IndexerModel;
import com.inqwise.indexer.IndexerQueue;
import com.inqwise.indexer.IndexerRepository;
import com.inqwise.indexer.IndexerStatus;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.RemoveDocumentActionItem;

import io.vertx.core.Future;

public class SubmitIndexActionsCommandHandler implements CommandHandler {
	private final IndexerRepository repository;
	private final IndexerLifecycleEventBus eventBus;
	private final IndexerQueue queue;

	public SubmitIndexActionsCommandHandler(
		IndexerRepository repository,
		IndexerLifecycleEventBus eventBus,
		IndexerQueue queue
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
		this.queue = Objects.requireNonNull(queue, "queue");
	}

	@Override
	public String getType() {
		return SubmitIndexActionsCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		SubmitIndexActionsCommand submit = new SubmitIndexActionsCommand(command.toJson());

		return validateActions(submit)
			.compose(ignored -> ensurePublishReady(submit))
			.compose(model -> publishLifecycle(model)
				.compose(ignored -> publishActions(submit)));
	}

	private Future<Void> validateActions(SubmitIndexActionsCommand submit) {
		for (IndexerActionItem action : submit.getActions()) {
			if (action.getActionType() == IndexerActionType.PUT_DOCUMENT) {
				PutDocumentActionItem put = (PutDocumentActionItem) action;
				if (!submit.getIndexName().equals(put.getIndexName())) {
					return Future.failedFuture(
						"Action index mismatch for command " + submit.getCommandId()
					);
				}
			}

			if (action.getActionType() == IndexerActionType.REMOVE_DOCUMENT) {
				RemoveDocumentActionItem remove = (RemoveDocumentActionItem) action;
				if (remove.getTargetName() != null
					&& submit.getTargetName() != null
					&& !submit.getTargetName().equals(remove.getTargetName())) {
					return Future.failedFuture(
						"Action target mismatch for command " + submit.getCommandId()
					);
				}
			}
		}

		return Future.succeededFuture();
	}

	private Future<IndexerModel> ensurePublishReady(SubmitIndexActionsCommand submit) {
		return repository.list()
			.compose(models -> {
				List<IndexerModel> matches = models.stream()
					.filter(model -> submit.getIndexName().equals(model.getIndexName()))
					.toList();

				if (matches.size() > 1) {
					return Future.failedFuture(
						"Multiple indexers found for index: " + submit.getIndexName()
					);
				}

				if (matches.isEmpty()) {
					return createIndexer(submit);
				}

				return verifyPublishReady(submit, matches.get(0));
			});
	}

	private Future<IndexerModel> createIndexer(SubmitIndexActionsCommand submit) {
		IndexerModel model = IndexerModel.builder()
			.withTargetId(submit.getTargetId())
			.withTargetName(submit.getTargetName())
			.withIndexName(submit.getIndexName())
			.withStatus(IndexerStatus.STARTED)
			.build();

		return repository.save(model)
			.compose(repository::get)
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Created indexer cannot be loaded for index: " + submit.getIndexName()
				)));
	}

	private Future<IndexerModel> verifyPublishReady(
		SubmitIndexActionsCommand submit,
		IndexerModel model
	) {
		if (model.getStatus() == IndexerStatus.DELETED) {
			return Future.failedFuture("Indexer is deleted: " + model.getIndexName());
		}

		if (!model.getStatus().isActive()) {
			return Future.failedFuture("Indexer is not active: " + model.getIndexName());
		}

		if (submit.getTargetId() != null
			&& model.getTargetId() != null
			&& !submit.getTargetId().equals(model.getTargetId())) {
			return Future.failedFuture("Indexer target id mismatch: " + model.getIndexName());
		}

		if (submit.getTargetName() != null
			&& model.getTargetName() != null
			&& !submit.getTargetName().equals(model.getTargetName())) {
			return Future.failedFuture("Indexer target name mismatch: " + model.getIndexName());
		}

		return Future.succeededFuture(model);
	}

	private Future<Void> publishLifecycle(IndexerModel model) {
		return eventBus.publish(new IndexerLifecycleChanged(
			model.getId(),
			getType(),
			model.getVersion()
		));
	}

	private Future<Void> publishActions(SubmitIndexActionsCommand submit) {
		List<Future<Void>> publishes = submit.getActions().stream()
			.map(queue::publish)
			.toList();

		return Future.join(publishes).mapEmpty();
	}
}
