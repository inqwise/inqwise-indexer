package com.inqwise.indexer.query;

import io.vertx.core.json.JsonObject;

public interface ReportRequestCodec<Q> {
	Q decode(JsonObject parameters);

	JsonObject encode(Q request);
}
