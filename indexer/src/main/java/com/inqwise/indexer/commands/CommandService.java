package com.inqwise.indexer.commands;

import io.vertx.core.Future;

public interface CommandService {
	Future<Void> submit(Command command);
}
