package com.inqwise.indexer.commands;

import io.vertx.core.json.JsonObject;

public interface Command {
	String getType();

	default String getCorrelationId() {
		return null;
	}

	JsonObject toJson();
}
