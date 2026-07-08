package com.inqwise.indexer.load;

import io.vertx.core.Future;

/** Draft boundary; service name, methods, grouping, and placement are provisional. */
public interface LoadManagementService {
	Future<IndexerLoadRecord> create(CreateLoadRequest request);

	Future<IndexerLoadRecord> start(StartLoadRequest request);

	Future<IndexerLoadRecord> recoverCreated(RecoverCreatedLoadRequest request);

	Future<IndexerLoadRecord> approvePublication(ApproveLoadPublicationRequest request);

	Future<Void> cancel(CancelLoadRequest request);
}
