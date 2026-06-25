package com.inqwise.indexer.commands;

import io.vertx.core.Future;

public interface CommandHandler {
	String getType();

	Future<Void> handle(Command command);
}
