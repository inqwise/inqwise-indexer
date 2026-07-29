package com.inqwise.indexer.publication;

import io.vertx.core.Future;

/**
 * Draft request/reply boundary for direct index publication lifecycle operations.
 * The service name, method names, method grouping, and placement of each function are
 * refactoring candidates until domain ownership and transport are finalized.
 */
public interface IndexPublicationService {
	IndexPublicationService UNSUPPORTED = new IndexPublicationService() {
		@Override
		public Future<PublicationReadinessResult> markReady(MarkIndexReadyRequest request) {
			return Future.failedFuture("Index publication service is required");
		}

		@Override
		public Future<IndexPublicationResult> publish(PublishIndexRequest request) {
			return Future.failedFuture("Index publication service is required");
		}

		@Override
		public Future<IndexPublicationResult> retire(RetireIndexRequest request) {
			return Future.failedFuture("Index publication service is required");
		}
	};

	Future<PublicationReadinessResult> markReady(MarkIndexReadyRequest request);

	Future<IndexPublicationResult> publish(PublishIndexRequest request);

	Future<IndexPublicationResult> retire(RetireIndexRequest request);
}
