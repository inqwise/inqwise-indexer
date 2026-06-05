package com.inqwise.indexer.load;

import java.time.Instant;

import io.vertx.core.json.JsonObject;

public record LoadRequest(
	Integer indexerId,
	Integer targetId,
	Integer liveIndexerId,
	String providerId,
	String targetName,
	String indexName,
	String queueName,
	Instant reloadStartAt,
	Instant liveReplayFrom,
	Instant sourceFrom,
	Instant sourceTo,
	JsonObject sourceQuery,
	String sourcePlaybookId
) {
}
