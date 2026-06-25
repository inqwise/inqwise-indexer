package com.inqwise.indexer.commands;

import io.vertx.core.Future;

public interface CommandEngine extends CommandService {
	CommandEngine register(CommandHandler handler);

	Future<Void> start();

	Future<Void> stop();
}
