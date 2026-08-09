package com.inqwise.indexer.service.action;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.routing.ActionDestination;
import com.inqwise.indexer.routing.SubmitIndexActionsCommand;
import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.hot.HotIndexActionsRequest;
import com.inqwise.indexer.hot.HotIndexActionsService;

import io.vertx.core.Future;

public class TargetActionServiceImpl implements TargetActionService {
	private final HotIndexActionsService hotActions;
	private final TargetActionPreparationRegistry preparations;

	public TargetActionServiceImpl(HotIndexActionsService hotActions) {
		this(hotActions, TargetActionPreparationRegistry.NONE);
	}

	public TargetActionServiceImpl(
		HotIndexActionsService hotActions,
		TargetActionPreparationRegistry preparations
	) {
		this.hotActions = Objects.requireNonNull(hotActions, "hotActions");
		this.preparations = Objects.requireNonNull(preparations, "preparations");
	}

	@Override
	public Future<TargetActionSubmitResult> submit(TargetActionSubmitRequest request) {
		try {
			validate(request);
			String submissionId = resolveSubmissionId(request);
			return preparations.prepare(request.getTargetName(), request.getActions())
				.compose(actions -> {
					validatePrepared(request.getActions(), actions);
					validateActions(actions);
					return hotActions.submit(HotIndexActionsRequest.builder()
						.withTargetName(request.getTargetName())
						.withTimestamp(request.getTimestamp())
						.withActions(actions)
						.build());
				})
				.map(TargetActionSubmitResult.builder()
					.withSubmissionId(submissionId)
					.withState(TargetActionSubmitState.ACCEPTED)
					.build())
				.recover(error -> Future.failedFuture(normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(normalize(error));
		}
	}

	private void validate(TargetActionSubmitRequest request) {
		if (request == null) {
			throw IndexerErrors.invalidRequest("Request is required");
		}

		if (request.getTargetName() == null || request.getTargetName().isBlank()) {
			throw IndexerErrors.invalidRequest("Target name is required");
		}

		validateActions(request.getActions());
	}

	private void validateActions(List<IndexerActionItem> actions) {
		if (actions == null || actions.isEmpty()) {
			throw IndexerErrors.invalidRequest("No actions submitted");
		}
		if (actions.size() > SubmitIndexActionsCommand.MAX_ACTIONS) {
			throw IndexerErrors.invalidRequest("Too many actions submitted: " + actions.size());
		}
		for (IndexerActionItem action : actions) {
			validateAction(action);
		}
	}

	private void validatePrepared(
		List<IndexerActionItem> submitted,
		List<IndexerActionItem> prepared
	) {
		if (submitted.size() != prepared.size()) {
			throw new IllegalStateException("Action preparation must preserve action count");
		}
		for (int index = 0; index < submitted.size(); index++) {
			if (submitted.get(index).getActionType() != prepared.get(index).getActionType()) {
				throw new IllegalStateException("Action preparation must preserve action types");
			}
		}
	}

	private void validateAction(IndexerActionItem action) {
		if (action == null) {
			throw IndexerErrors.invalidRequest("Action is required");
		}

		if (action.getActionType() != IndexerActionType.PUT_DOCUMENT
			&& action.getActionType() != IndexerActionType.REMOVE_DOCUMENT) {
			throw IndexerErrors.invalidRequest(
				"Target action supports only document mutation actions: " + action.getActionType()
			);
		}

		if (!ActionDestination.from(action).isEmpty()) {
			throw IndexerErrors.invalidRequest(
				"Target action request actions must not include concrete destination fields"
			);
		}

		if (action.getActionType() == IndexerActionType.PUT_DOCUMENT) {
			PutDocumentActionItem put = (PutDocumentActionItem) action;
			int documentBytes = put.getDocument().encode().getBytes(StandardCharsets.UTF_8).length;
			if (documentBytes > SubmitIndexActionsCommand.MAX_DOCUMENT_BYTES) {
				throw IndexerErrors.invalidRequest("Document is too large: " + documentBytes);
			}
		}
	}

	private String resolveSubmissionId(TargetActionSubmitRequest request) {
		if (request.getSubmissionId() == null || request.getSubmissionId().isBlank()) {
			return UUID.randomUUID().toString();
		}

		return request.getSubmissionId();
	}

	private Throwable normalize(Throwable error) {
		if (error instanceof InvalidTargetActionPreparationException invalid) {
			return IndexerErrors.invalidRequest(invalid.getMessage());
		}
		return IndexerErrors.normalize(error);
	}
}
