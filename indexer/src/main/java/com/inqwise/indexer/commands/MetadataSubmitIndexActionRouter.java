package com.inqwise.indexer.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.RemoveDocumentActionItem;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.IndexerRuntimeStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.TargetStatus;

import io.vertx.core.Future;

class MetadataSubmitIndexActionRouter {
	private final DocumentStoreMetadataRepository repository;

	MetadataSubmitIndexActionRouter(DocumentStoreMetadataRepository repository) {
		this.repository = Objects.requireNonNull(repository, "repository");
	}

	Future<List<RoutedIndexActions>> route(SubmitIndexActionsCommand submit) {
		if (submit.getActions().isEmpty()) {
			return Future.failedFuture("No actions submitted for command " + submit.getCommandId());
		}

		Future<Map<IndexerRecord, List<IndexerActionItem>>> actionsByIndexer =
			Future.succeededFuture(new LinkedHashMap<>());

		for (IndexerActionItem action : submit.getActions()) {
			ActionDestination destination = ActionDestination.from(action);
			if (destination.isEmpty()) {
				return Future.failedFuture("Action destination is missing for command " + submit.getCommandId());
			}

			actionsByIndexer = actionsByIndexer.compose(groups ->
				resolveIndexers(submit, destination)
					.compose(indexers -> {
						for (IndexerRecord indexer : indexers) {
							groups.computeIfAbsent(indexer, ignored -> new ArrayList<>())
								.add(toConcreteAction(action, indexer));
						}

						return Future.succeededFuture(groups);
					}));
		}

		return actionsByIndexer.map(groups -> groups.entrySet().stream()
			.map(entry -> new RoutedIndexActions(
				entry.getKey().id(),
				entry.getKey().version(),
				getQueueName(entry.getKey()),
				entry.getValue()
			))
			.toList());
	}

	private Future<List<IndexerRecord>> resolveIndexers(
		SubmitIndexActionsCommand submit,
		ActionDestination destination
	) {
		if (destination.indexerId() != null) {
			return repository.getIndexerById(destination.indexerId())
				.compose(found -> found
					.map(indexer -> verifyIndexer(destination, indexer)
						.map(List::of))
					.orElseGet(() -> Future.failedFuture(
						"Indexer not found: " + destination.indexerId()
					)));
		}

		if (destination.targetId() == null) {
			return Future.failedFuture(
				"Action target id is required for metadata routing in command " + submit.getCommandId()
			);
		}

		return repository.getTargetById(destination.targetId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Target not found: " + destination.targetId()
				)))
			.compose(target -> {
				if (target.status() != TargetStatus.ACTIVE) {
					return Future.failedFuture("Target is not active: " + destination.targetId());
				}

				return repository.listWritableIndexersByTargetId(destination.targetId());
			})
			.compose(indexers -> {
				List<IndexerRecord> matches = indexers.stream()
					.filter(indexer -> indexer.runtimeStatus() == IndexerRuntimeStatus.STARTED
						|| indexer.runtimeStatus() == IndexerRuntimeStatus.COMPLETED)
					.filter(indexer -> destination.indexName() == null
						|| destination.indexName().equals(indexer.indexName()))
					.toList();

				if (matches.isEmpty()) {
					return Future.failedFuture(
						"No writable indexers found for target id: " + destination.targetId()
					);
				}

				return Future.succeededFuture(matches);
			});
	}

	private Future<IndexerRecord> verifyIndexer(
		ActionDestination destination,
		IndexerRecord indexer
	) {
		if (indexer.runtimeStatus() != IndexerRuntimeStatus.STARTED
			&& indexer.runtimeStatus() != IndexerRuntimeStatus.COMPLETED) {
			return Future.failedFuture("Indexer is not active: " + indexer.indexName());
		}

		if (indexer.mutationState() != MutationState.WRITABLE) {
			return Future.failedFuture("Indexer is not writable: " + indexer.indexName());
		}

		if (destination.targetId() != null && !destination.targetId().equals(indexer.targetId())) {
			return Future.failedFuture("Indexer target id mismatch: " + indexer.indexName());
		}

		if (destination.indexName() != null && !destination.indexName().equals(indexer.indexName())) {
			return Future.failedFuture("Indexer index mismatch: " + indexer.indexName());
		}

		return Future.succeededFuture(indexer);
	}

	private IndexerActionItem toConcreteAction(
		IndexerActionItem action,
		IndexerRecord indexer
	) {
		return switch (action.getActionType()) {
			case PUT_DOCUMENT -> {
				PutDocumentActionItem put = (PutDocumentActionItem) action;
				yield PutDocumentActionItem.builder()
					.withTargetId(indexer.targetId())
					.withIndexerId(indexer.id())
					.withIndexName(indexer.indexName())
					.withUid(put.getUid())
					.withSequence(put.getSequence())
					.withMutationId(put.getMutationId())
					.withDocument(put.getDocument())
					.build();
			}
			case REMOVE_DOCUMENT -> {
				RemoveDocumentActionItem remove = (RemoveDocumentActionItem) action;
				yield RemoveDocumentActionItem.builder()
					.withTargetId(indexer.targetId())
					.withIndexerId(indexer.id())
					.withTargetName(remove.getTargetName())
					.withIndexName(indexer.indexName())
					.withUid(remove.getUid())
					.withSequence(remove.getSequence())
					.withMutationId(remove.getMutationId())
					.build();
			}
			case COMPLETE -> CompleteIndexActionItem.builder()
				.withTargetId(indexer.targetId())
				.withIndexerId(indexer.id())
				.build();
		};
	}

	private String getQueueName(IndexerRecord indexer) {
		return indexer.queueName() == null ? indexer.indexName() : indexer.queueName();
	}
}
