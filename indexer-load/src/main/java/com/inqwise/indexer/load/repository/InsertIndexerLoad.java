package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;


import java.time.Instant;

import io.vertx.core.json.JsonObject;

public record InsertIndexerLoad(
	Integer indexerId,
	Integer targetId,
	Integer liveIndexerId,
	LiveWriterPolicy liveWriterPolicy,
	String providerId,
	IndexerLoadState state,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	Instant sourceFrom,
	Instant sourceTo,
	JsonObject sourceQuery,
	String sourcePlaybookId,
	boolean reviewRequired
) {
}
