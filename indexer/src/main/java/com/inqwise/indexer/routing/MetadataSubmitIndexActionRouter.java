package com.inqwise.indexer.routing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.inqwise.indexer.actions.CatchUpBarrierActionItem;
import com.inqwise.indexer.actions.CompleteIndexActionItem;
import com.inqwise.indexer.catalog.indexers.IndexResourceOwnership;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.actions.Actions;
import com.inqwise.indexer.actions.IndexerActionRouteContext;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.routing.ActionDestination;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.routing.RoutedIndexActions;
import com.inqwise.indexer.routing.SubmitIndexActionsCommand;
import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetDefinitionProvider;
import com.inqwise.indexer.catalog.targets.ConcreteTargetKey;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.metadata.MetadataIndexerModels;
import com.inqwise.indexer.catalog.targets.TargetPeriod;
import com.inqwise.indexer.catalog.targets.TargetPeriodResolver;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetRecord;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.metadata.UpdateTargetProvisioningState;
import com.inqwise.indexer.providers.ActionReceiveReadiness;
import com.inqwise.indexer.providers.IndexerActionReceiveCapability;
import com.inqwise.indexer.providers.PrepareIndexerForActionsRequest;
import com.inqwise.indexer.providers.PreparedIndexers;
import com.inqwise.indexer.provisioning.CreateIndexerProvisioningRequest;
import com.inqwise.indexer.provisioning.GeneratedIndexerResources;
import com.inqwise.indexer.provisioning.IndexerProvisioningService;
import com.inqwise.indexer.provisioning.IndexerResourceNameGenerator;
import com.inqwise.indexer.provisioning.ProvisionedIndexer;
import com.inqwise.indexer.publication.IndexPublicationService;
import com.inqwise.indexer.publication.MarkIndexReadyRequest;
import com.inqwise.indexer.publication.PublishIndexRequest;

import io.vertx.core.Future;

class MetadataSubmitIndexActionRouter {
	private final DocumentStoreMetadataRepository repository;
	private final TargetDefinitionProvider targetDefinitionProvider;
	private final List<IndexerActionReceiveCapability> receiveCapabilities;
	private final IndexerProvisioningService provisioningService;
	private final IndexPublicationService publicationService;
	private final TargetPeriodResolver periodResolver = new TargetPeriodResolver();

	MetadataSubmitIndexActionRouter(
		DocumentStoreMetadataRepository repository,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerProvisioningService provisioningService,
		IndexPublicationService publicationService,
		List<IndexerActionReceiveCapability> receiveCapabilities
	) {
		this.repository = Objects.requireNonNull(repository, "repository");
		this.targetDefinitionProvider = Objects.requireNonNull(
			targetDefinitionProvider,
			"targetDefinitionProvider"
		);
		this.receiveCapabilities = List.copyOf(Objects.requireNonNull(
			receiveCapabilities,
			"receiveCapabilities"
		));
		this.provisioningService = Objects.requireNonNull(
			provisioningService,
			"provisioningService"
		);
		this.publicationService = publicationService == null
			? IndexPublicationService.UNSUPPORTED
			: publicationService;
	}

	Future<List<RoutedIndexActions>> route(SubmitIndexActionsCommand submit) {
		if (submit.getActions().isEmpty()) {
			return Future.failedFuture("No actions submitted for command " + submit.getCorrelationId());
		}

		MetadataRoutingContext routingContext = new MetadataRoutingContext();
		Future<Map<IndexerRouteKey, IndexerActionGroup>> actionsByIndexer =
			Future.succeededFuture(new LinkedHashMap<>());

		for (IndexerActionItem action : submit.getActions()) {
			ActionDestination destination = ActionDestination.from(action);
			if (destination.isEmpty() && !hasPublicTarget(submit)) {
				return Future.failedFuture("Action destination is missing for command " + submit.getCorrelationId());
			}

			actionsByIndexer = actionsByIndexer.compose(groups ->
				resolveIndexers(submit, action, destination, routingContext)
					.compose(indexers -> {
						for (IndexerModel indexer : indexers) {
							groups.computeIfAbsent(
								IndexerRouteKey.from(indexer),
								ignored -> IndexerActionGroup.builder()
									.withIndexer(indexer)
									.withActions(new ArrayList<>())
									.build()
							).actions().add(toConcreteAction(action, indexer));
						}

						return Future.succeededFuture(groups);
					}));
		}

		return actionsByIndexer.map(groups -> groups.entrySet().stream()
			.map(entry -> {
				IndexerActionGroup group = entry.getValue();
				IndexerModel indexer = group.indexer();
				return RoutedIndexActions.builder()
					.withIndexerId(indexer.getId())
					.withTargetId(indexer.getTargetId())
					.withIndexerVersion(indexer.getVersion())
					.withQueueName(getQueueName(indexer))
					.withActions(group.actions())
					.withMetadataChanged(routingContext.metadataChanged(indexer.getId()))
					.build();
			})
			.toList());
	}

	private Future<List<IndexerModel>> resolveIndexers(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		ActionDestination destination,
		MetadataRoutingContext routingContext
	) {
		if (destination.indexerId() != null) {
			return repository.getIndexerById(destination.indexerId())
				.compose(found -> found
					.map(indexer -> verifyIndexer(destination, indexer)
						.map(MetadataIndexerModels::fromRecord)
						.map(List::of))
					.orElseGet(() -> Future.failedFuture(
						CommandFailure.stableInvalid("Indexer not found: " + destination.indexerId())
					)));
		}

		if (destination.targetId() == null && hasPublicTarget(submit)) {
			return resolveConcreteTarget(submit)
				.compose(target -> resolveIndexersByTarget(
					submit,
					action,
					destination,
					target.record(),
					target.autoProvisionOnWrite(),
					target.autoPublishOnWrite(),
					routingContext
				));
		}

		if (destination.targetId() == null) {
			return Future.failedFuture(
				"Action target id is required for metadata routing in command " + submit.getCorrelationId()
			);
		}

		return repository.getTargetById(destination.targetId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					CommandFailure.stableInvalid("Target not found: " + destination.targetId())
				)))
			.compose(target -> resolveIndexersByTarget(
				submit,
				action,
				destination,
				target,
				false,
				false,
				routingContext
			));
	}

	private Future<List<IndexerModel>> resolveIndexersByTarget(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		ActionDestination destination,
		TargetRecord target,
		boolean autoProvision,
		boolean autoPublish,
		MetadataRoutingContext routingContext
	) {
		if (target.status() != TargetStatus.ACTIVE) {
			return Future.failedFuture("Target is not active: " + target.id());
		}

		if (target.provisioningState() == TargetProvisioningState.PROVISIONING) {
			return Future.failedFuture(CommandFailure.retryable(
				"Target provisioning is in progress: " + target.id()
			));
		}

		if (target.provisioningState() == TargetProvisioningState.FAILED) {
			return Future.failedFuture(CommandFailure.finalFailure(
				"Target provisioning failed: " + target.id()
			));
		}

		return repository.listWritableIndexersByTargetId(target.id())
			.compose(indexers -> {
				List<IndexerRecord> activeCandidates = indexers.stream()
					.filter(indexer -> indexer.runtimeState() == IndexerRuntimeState.ACTIVE)
					.filter(indexer -> destination.indexName() == null
						|| destination.indexName().equals(indexer.indexName()))
					.toList();

				List<IndexerRecord> matches = activeCandidates.stream()
					.filter(indexer -> indexer.role() == IndexerRole.LIVE_WRITER)
					.toList();

				if (matches.isEmpty()) {
					return prepareReceivers(submit, action, activeCandidates, routingContext)
						.compose(prepared -> {
							if (!prepared.isEmpty()) {
								return Future.succeededFuture(prepared);
							}

							return autoProvision
								? ensureWritableIndexer(target, autoPublish, routingContext)
									.map(MetadataIndexerModels::fromRecord)
									.map(List::of)
								: Future.failedFuture(CommandFailure.stableInvalid(
									"No writable indexers found for target id: " + target.id()
								));
						});
				}

				return Future.succeededFuture(matches.stream()
					.map(MetadataIndexerModels::fromRecord)
					.toList());
			});
	}

	private Future<List<IndexerModel>> prepareReceivers(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		List<IndexerRecord> candidates,
		MetadataRoutingContext routingContext
	) {
		Future<List<IndexerModel>> prepared = Future.succeededFuture(List.of());

		for (IndexerRecord candidate : candidates) {
			prepared = prepared.compose(current -> {
				if (!current.isEmpty()) {
					return Future.succeededFuture(current);
				}

				return prepareReceiver(submit, action, candidate, routingContext);
			});
		}

		return prepared;
	}

	private Future<List<IndexerModel>> prepareReceiver(
		SubmitIndexActionsCommand submit,
		IndexerActionItem action,
		IndexerRecord candidate,
		MetadataRoutingContext routingContext
	) {
		Future<List<IndexerModel>> prepared = Future.succeededFuture(List.of());
		IndexerModel candidateModel = MetadataIndexerModels.fromRecord(candidate);

		for (IndexerActionReceiveCapability capability : receiveCapabilities) {
			prepared = prepared.compose(current -> {
				if (!current.isEmpty()) {
					return Future.succeededFuture(current);
				}

				return capability.canReceive(candidateModel, action)
					.compose(readiness -> {
						if (readiness == ActionReceiveReadiness.NO) {
							return Future.succeededFuture(List.of());
						}
						if (readiness == ActionReceiveReadiness.YES) {
							return Future.succeededFuture(List.of(candidateModel));
						}

						return capability.prepareToReceive(PrepareIndexerForActionsRequest.builder()
							.withCommandId(submit.getCorrelationId())
							.withIndexer(candidateModel)
							.withActions(List.of(action))
							.withTimestamp(submit.getTimestamp())
							.build()).map(result -> preparedIndexers(result, routingContext));
					});
			});
		}

		return prepared;
	}

	private List<IndexerModel> preparedIndexers(
		PreparedIndexers prepared,
		MetadataRoutingContext routingContext
	) {
		if (prepared.metadataChanged()) {
			for (IndexerModel indexer : prepared.indexers()) {
				routingContext.markMetadataChanged(indexer.getId());
			}
		}

		return prepared.indexers();
	}

	private Future<ResolvedTarget> resolveConcreteTarget(SubmitIndexActionsCommand submit) {
		return resolveTargetDefinition(submit)
			.compose(targetDefinition -> {
				if (targetDefinition.periodStrategy() != TargetPeriodStrategy.NONE
					&& submit.getTimestamp() == null) {
					return Future.failedFuture(
						"Timestamp is required for target period strategy: "
							+ targetDefinition.periodStrategy()
					);
				}

				TargetPeriod period;
				try {
					period = periodResolver.resolve(
						targetDefinition.periodStrategy(),
						submit.getTimestamp()
					);
				} catch (RuntimeException error) {
					return Future.failedFuture(error);
				}

				return repository.getTargetByDefinitionAndPeriod(ConcreteTargetKey.builder()
					.withTargetName(targetDefinition.targetName())
					.withPeriodKey(period.key())
					.build()).compose(found -> found
					.map(target -> Future.succeededFuture(ResolvedTarget.builder()
						.withRecord(target)
						.withAutoProvisionOnWrite(targetDefinition.autoProvisionOnWrite())
						.withAutoPublishOnWrite(targetDefinition.autoPublishOnWrite())
						.build()))
					.orElseGet(() -> {
						if (!targetDefinition.autoProvisionOnWrite()) {
							return Future.failedFuture(CommandFailure.stableInvalid(
								"Auto provisioning is disabled for target: "
									+ targetDefinition.targetName()
							));
						}

						return repository.ensureTarget(targetDefinition.targetName(), period)
							.map(target -> ResolvedTarget.builder()
								.withRecord(target)
								.withAutoProvisionOnWrite(true)
								.withAutoPublishOnWrite(targetDefinition.autoPublishOnWrite())
								.build());
					}));
			});
	}

	private Future<TargetDefinition> resolveTargetDefinition(SubmitIndexActionsCommand submit) {
		return submit.getTargetName() == null
			? Future.failedFuture(CommandFailure.stableInvalid(
				"Target reference is missing for command " + submit.getCorrelationId()
			))
			: targetDefinitionProvider.getByName(submit.getTargetName())
				.compose(found -> found
					.map(Future::succeededFuture)
					.orElseGet(() -> Future.failedFuture(
						CommandFailure.stableInvalid(
							"Target definition not found by name: " + submit.getTargetName()
						)
					)));
	}

	private Future<IndexerRecord> ensureWritableIndexer(
		TargetRecord target,
		boolean autoPublish,
		MetadataRoutingContext routingContext
	) {
		GeneratedIndexerResources resources = IndexerResourceNameGenerator.forTarget(target.targetName());
		return repository.updateTargetProvisioningState(UpdateTargetProvisioningState.builder()
			.withId(target.id())
			.withProvisioningState(TargetProvisioningState.PROVISIONING)
			.withExpectedVersion(target.version())
			.build()).recover(error -> Future.failedFuture(CommandFailure.retryable(
			"Target provisioning lock changed: " + target.id(),
			error
		))).compose(ignored -> provisioningService.createIndexer(
			CreateIndexerProvisioningRequest.builder()
				.withPrefix(resources.prefix())
				.withTargetId(target.id())
				.withIndexName(resources.indexName())
				.withQueueName(resources.queueName())
				.withRole(IndexerRole.LIVE_WRITER)
				.withIndexOwnership(IndexResourceOwnership.OWNER)
				.withRuntimeState(IndexerRuntimeState.ACTIVE)
				.build()
		))
			.onSuccess(indexer -> routingContext.markMetadataChanged(indexer.indexerId()))
			.compose(indexer -> repository.getTargetById(target.id())
				.compose(found -> found
					.map(current -> repository.updateTargetProvisioningState(
						UpdateTargetProvisioningState.builder()
							.withId(target.id())
							.withProvisioningState(TargetProvisioningState.READY)
							.withExpectedVersion(current.version())
							.build()
					).map(indexer))
					.orElseGet(() -> Future.failedFuture("Target not found: " + target.id()))))
			.compose(this::getProvisionedIndexer)
			.compose(indexer -> autoPublish
				? publishProvisionedIndexer(indexer)
				: Future.succeededFuture(indexer))
			.recover(error -> recoverWritableIndexerProvisioning(target, error));
	}

	private Future<IndexerRecord> publishProvisionedIndexer(IndexerRecord indexer) {
		return repository.getPublicationByIndexerId(indexer.id())
			.compose(found -> found
				.map(publication -> publicationService.markReady(MarkIndexReadyRequest.builder()
					.withPublicationId(publication.id())
					.withReason("auto publish on write")
					.withExpectedVersion(publication.version())
					.build()))
				.orElseGet(() -> Future.failedFuture(
					"Publication not found for indexer: " + indexer.id()
				)))
			.compose(ignored -> publicationService.publish(PublishIndexRequest.builder()
				.withIndexerId(indexer.id())
				.withExpectedVersion(indexer.version())
				.build()))
			.compose(ignored -> repository.getIndexerById(indexer.id()))
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer not found: " + indexer.id())));
	}

	private Future<IndexerRecord> getProvisionedIndexer(ProvisionedIndexer provisioned) {
		return repository.getIndexerById(provisioned.indexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture(
					"Provisioned indexer not found: " + provisioned.indexerId()
				)));
	}

	private Future<IndexerRecord> recoverWritableIndexerProvisioning(
		TargetRecord target,
		Throwable error
	) {
		return repository.getTargetById(target.id())
			.compose(found -> {
				if (found.isEmpty()) {
					return Future.failedFuture(error);
				}

				TargetRecord current = found.get();
				if (current.provisioningState() == TargetProvisioningState.FAILED) {
					return Future.failedFuture(CommandFailure.finalFailure(
						"Target provisioning failed: " + target.id(),
						error
					));
				}

				if (current.version() != target.version() + 1
					|| current.provisioningState() != TargetProvisioningState.PROVISIONING) {
					return Future.failedFuture(CommandFailure.retryable(
						"Target provisioning changed: " + target.id(),
						error
					));
				}

				return repository.updateTargetProvisioningState(UpdateTargetProvisioningState.builder()
					.withId(target.id())
					.withProvisioningState(TargetProvisioningState.FAILED)
					.withExpectedVersion(current.version())
					.build()).compose(ignored -> Future.failedFuture(CommandFailure.retryable(
					"Writable indexer provisioning failed: " + target.id(),
					error
				)));
			});
	}

	private Future<IndexerRecord> verifyIndexer(
		ActionDestination destination,
		IndexerRecord indexer
	) {
		if (indexer.status() != IndexerStatus.AVAILABLE
			|| indexer.provisioningState() != IndexerProvisioningState.READY
			|| indexer.runtimeState() != IndexerRuntimeState.ACTIVE) {
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
		IndexerModel indexer
	) {
		return switch (action.getActionType()) {
			case PUT_DOCUMENT -> {
				yield routeAction(action, indexer);
			}
			case REMOVE_DOCUMENT -> routeAction(action, indexer);
			case COMPLETE -> CompleteIndexActionItem.builder()
				.withTargetId(indexer.getTargetId())
				.withIndexerId(indexer.getId())
				.build();
			case CATCH_UP_BARRIER -> {
				CatchUpBarrierActionItem barrier = (CatchUpBarrierActionItem) action;
				yield CatchUpBarrierActionItem.builder()
					.withTargetId(indexer.getTargetId())
					.withIndexerId(indexer.getId())
					.withBarrierId(barrier.getBarrierId())
					.withBarrierTimestamp(barrier.getBarrierTimestamp())
					.build();
			}
		};
	}

	private IndexerActionItem routeAction(
		IndexerActionItem action,
		IndexerModel indexer
	) {
		return Actions.getProvider(action.getActionType())
			.router()
			.route(IndexerActionRouteContext.builder()
				.withTargetId(indexer.getTargetId())
				.withIndexerId(indexer.getId())
				.withTargetName(indexer.getTargetName())
				.withIndexName(indexer.getIndexName())
				.withQueueName(getQueueName(indexer))
				.withRole(indexer.getRole())
				.build(), action, IndexerActionRouteMode.DIRECT)
			.orElseThrow(() -> new IllegalArgumentException(
				"Action is not accepted by indexer: " + indexer.getIndexName()
			));
	}

	private String getQueueName(IndexerModel indexer) {
		return indexer.getQueueName() == null ? indexer.getIndexName() : indexer.getQueueName();
	}

	private boolean hasPublicTarget(SubmitIndexActionsCommand submit) {
		return submit.getTargetName() != null;
	}

	private static class MetadataRoutingContext {
		private final Set<Integer> metadataChangedIndexerIds = new HashSet<>();

		private void markMetadataChanged(Integer indexerId) {
			metadataChangedIndexerIds.add(indexerId);
		}

		private boolean metadataChanged(Integer indexerId) {
			return metadataChangedIndexerIds.contains(indexerId);
		}
	}

	private record IndexerActionGroup(
		IndexerModel indexer,
		List<IndexerActionItem> actions
	) {
		private static Builder builder() {
			return new Builder();
		}

		private static final class Builder {
			private IndexerModel indexer;
			private List<IndexerActionItem> actions;

			private Builder withIndexer(IndexerModel value) {
				indexer = value;
				return this;
			}

			private Builder withActions(List<IndexerActionItem> value) {
				actions = value == null ? null : new ArrayList<>(value);
				return this;
			}

			private IndexerActionGroup build() {
				return new IndexerActionGroup(
					Objects.requireNonNull(indexer, "indexer"),
					new ArrayList<>(Objects.requireNonNull(actions, "actions"))
				);
			}
		}
	}

	private record IndexerRouteKey(
		Integer id,
		Integer targetId,
		String targetName,
		String indexName,
		String queueName,
		IndexerRole role,
		long version
	) {
		private static IndexerRouteKey from(IndexerModel indexer) {
			return builder()
				.withId(indexer.getId())
				.withTargetId(indexer.getTargetId())
				.withTargetName(indexer.getTargetName())
				.withIndexName(indexer.getIndexName())
				.withQueueName(indexer.getQueueName())
				.withRole(indexer.getRole())
				.withVersion(indexer.getVersion())
				.build();
		}

		private static Builder builder() {
			return new Builder();
		}

		private static final class Builder {
			private Integer id;
			private Integer targetId;
			private String targetName;
			private String indexName;
			private String queueName;
			private IndexerRole role;
			private long version;

			private Builder withId(Integer value) {
				id = value;
				return this;
			}

			private Builder withTargetId(Integer value) {
				targetId = value;
				return this;
			}

			private Builder withTargetName(String value) {
				targetName = value;
				return this;
			}

			private Builder withIndexName(String value) {
				indexName = value;
				return this;
			}

			private Builder withQueueName(String value) {
				queueName = value;
				return this;
			}

			private Builder withRole(IndexerRole value) {
				role = value;
				return this;
			}

			private Builder withVersion(long value) {
				version = value;
				return this;
			}

			private IndexerRouteKey build() {
				return new IndexerRouteKey(
					Objects.requireNonNull(id, "id"),
					Objects.requireNonNull(targetId, "targetId"),
					Objects.requireNonNull(targetName, "targetName"),
					Objects.requireNonNull(indexName, "indexName"),
					queueName,
					Objects.requireNonNull(role, "role"),
					version
				);
			}
		}
	}

	private record ResolvedTarget(
		TargetRecord record,
		boolean autoProvisionOnWrite,
		boolean autoPublishOnWrite
	) {
		private static Builder builder() {
			return new Builder();
		}

		private static final class Builder {
			private TargetRecord record;
			private boolean autoProvisionOnWrite;
			private boolean autoPublishOnWrite;

			private Builder withRecord(TargetRecord value) {
				record = value;
				return this;
			}

			private Builder withAutoProvisionOnWrite(boolean value) {
				autoProvisionOnWrite = value;
				return this;
			}

			private Builder withAutoPublishOnWrite(boolean value) {
				autoPublishOnWrite = value;
				return this;
			}

			private ResolvedTarget build() {
				return new ResolvedTarget(
					Objects.requireNonNull(record, "record"),
					autoProvisionOnWrite,
					autoPublishOnWrite
				);
			}
		}
	}
}
