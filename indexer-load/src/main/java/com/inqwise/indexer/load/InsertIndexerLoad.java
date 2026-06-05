package com.inqwise.indexer.load;

import java.time.Instant;

import io.vertx.core.json.JsonObject;

public record InsertIndexerLoad(
	Integer indexerId,
	Integer targetId,
	Integer liveIndexerId,
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
