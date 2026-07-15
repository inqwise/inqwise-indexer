package com.inqwise.indexer.service.action;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.commands.ActionDestination;
import com.inqwise.indexer.commands.SubmitIndexActionsCommand;
import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.hot.HotIndexActionsRequest;
import com.inqwise.indexer.hot.HotIndexActionsService;

import io.vertx.core.Future;

public class TargetActionServiceImpl implements TargetActionService {
	private final HotIndexActionsService hotActions;

	public TargetActionServiceImpl(HotIndexActionsService hotActions) {
		this.hotActions = Objects.requireNonNull(hotActions, "hotActions");
	}

	@Override
	public Future<TargetActionSubmitResult> submit(TargetActionSubmitRequest request) {
		try {
			validate(request);
			String submissionId = resolveSubmissionId(request);
			return hotActions.submit(new HotIndexActionsRequest(
				request.getTargetName(),
				request.getTimestamp(),
				request.getActions()
			)).map(new TargetActionSubmitResult()
				.setSubmissionId(submissionId)
				.setState(TargetActionSubmitState.ACCEPTED))
				.recover(error -> Future.failedFuture(IndexerErrors.normalize(error)));
		} catch (Throwable error) {
			return Future.failedFuture(IndexerErrors.normalize(error));
		}
	}

	private void validate(TargetActionSubmitRequest request) {
		if (request == null) {
			throw IndexerErrors.invalidRequest("Request is required");
		}

		if (request.getTargetName() == null || request.getTargetName().isBlank()) {
			throw IndexerErrors.invalidRequest("Target name is required");
		}

		if (request.getActions() == null || request.getActions().isEmpty()) {
			throw IndexerErrors.invalidRequest("No actions submitted");
		}

		if (request.getActions().size() > SubmitIndexActionsCommand.MAX_ACTIONS) {
			throw IndexerErrors.invalidRequest(
				"Too many actions submitted: " + request.getActions().size()
			);
		}

		for (IndexerActionItem action : request.getActions()) {
			validateAction(action);
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
}
