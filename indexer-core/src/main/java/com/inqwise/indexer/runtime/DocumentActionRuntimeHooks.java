package com.inqwise.indexer.runtime;

import io.vertx.core.Future;

public interface DocumentActionRuntimeHooks {
	DocumentActionRuntimeHooks NONE = new DocumentActionRuntimeHooks() {
	};

	default Future<Void> beforeAction(DocumentActionExecutionContext context) {
		return Future.succeededFuture();
	}

	default Future<Void> afterWriteBeforeCommit(DocumentActionExecutionContext context) {
		return Future.succeededFuture();
	}

	default Future<Void> afterCommit(DocumentActionExecutionContext context) {
		return Future.succeededFuture();
	}
}
