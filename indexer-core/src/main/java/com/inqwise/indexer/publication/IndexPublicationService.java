package com.inqwise.indexer.publication;

import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.PublicationRecord;

import io.vertx.core.Future;

/**
 * Draft request/reply boundary for direct index publication lifecycle operations.
 * The service name, method names, method grouping, and placement of each function are
 * refactoring candidates until domain ownership and transport are finalized.
 */
public interface IndexPublicationService {
	Future<PublicationRecord> markReady(MarkIndexReadyRequest request);

	Future<IndexerRecord> publish(PublishIndexRequest request);

	Future<IndexerRecord> retire(RetireIndexRequest request);
}
