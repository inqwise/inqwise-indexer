package com.inqwise.indexer.documents;

import io.vertx.core.Future;

public interface DocumentQueryEngine {
	Future<DocumentQueryResult> query(DocumentQuery query);
}
