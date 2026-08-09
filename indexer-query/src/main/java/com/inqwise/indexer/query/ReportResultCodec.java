package com.inqwise.indexer.query;

import io.vertx.core.json.JsonObject;

public interface ReportResultCodec<R> {
	R decode(JsonObject payload);

	JsonObject encode(R result);
}
