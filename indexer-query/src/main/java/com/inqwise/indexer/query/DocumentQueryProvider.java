package com.inqwise.indexer.query;

import io.vertx.core.Future;

public interface DocumentQueryProvider {
	Future<DocumentQueryResult> execute(DocumentQueryExecution execution);
}
