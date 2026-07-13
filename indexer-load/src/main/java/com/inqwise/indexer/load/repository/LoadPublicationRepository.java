package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadRecord;

import io.vertx.core.Future;

public interface LoadPublicationRepository {
	Future<LoadPublication> publish(IndexerLoadRecord load);
}
