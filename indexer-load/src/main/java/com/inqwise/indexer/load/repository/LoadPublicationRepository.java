package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadRecord;

import io.vertx.core.Future;

public interface LoadPublicationRepository {
	Future<LoadPublication> prepare(IndexerLoadRecord load);

	/** Applies the saved metadata transition, accepting exact already-applied replay. */
	Future<Void> publish(LoadPublication publication);
}
