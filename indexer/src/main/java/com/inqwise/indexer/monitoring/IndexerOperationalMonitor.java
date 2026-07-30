package com.inqwise.indexer.monitoring;

import com.inqwise.indexer.actions.IndexerActionType;

public interface IndexerOperationalMonitor {
	IndexerOperationalMonitor NOOP = new IndexerOperationalMonitor() {
	};

	enum LifecycleOperation {
		PROVISION,
		PUBLISH,
		RECONCILE,
		RESET_QUEUE,
		DELETE
	}

	default void actionIntake(IndexerActionType actionType, boolean accepted) {
	}

	default void lifecycleStarted(LifecycleOperation operation) {
	}

	default void lifecycleCompleted(
		LifecycleOperation operation,
		boolean succeeded
	) {
	}

	default void runtimeConvergence(int desired, int attached, int drift) {
	}
}
