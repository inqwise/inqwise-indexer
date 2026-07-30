package com.inqwise.indexer.documents;

import io.vertx.core.Future;

public interface IndexerDocumentQueryProvider {
	Future<DocumentIndexQueryResult> query(DocumentIndexQuery query);
}
