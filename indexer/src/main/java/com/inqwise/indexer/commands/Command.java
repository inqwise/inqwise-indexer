package com.inqwise.indexer.commands;

import io.vertx.core.json.JsonObject;

public interface Command {
	String getType();

	JsonObject toJson();
}
