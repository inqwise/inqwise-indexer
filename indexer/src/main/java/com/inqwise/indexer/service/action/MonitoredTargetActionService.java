package com.inqwise.indexer.service.action;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;

import io.vertx.core.Future;

final class MonitoredTargetActionService implements TargetActionService {
	private final TargetActionService delegate;
	private final IndexerOperationalMonitor monitor;

	MonitoredTargetActionService(
		TargetActionService delegate,
		IndexerOperationalMonitor monitor
	) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.monitor = Objects.requireNonNull(monitor, "monitor");
	}

	@Override
	public Future<TargetActionSubmitResult> submit(TargetActionSubmitRequest request) {
		List<IndexerActionItem> actions = request == null || request.getActions() == null
			? List.of()
			: request.getActions().stream()
				.filter(Objects::nonNull)
				.toList();
		Future<TargetActionSubmitResult> submitted = delegate.submit(request);
		submitted.onComplete(result -> actions.forEach(action ->
			monitor.actionIntake(action.getActionType(), result.succeeded())
		));
		return submitted;
	}
}
