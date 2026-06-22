package com.inqwise.indexer.commands;

@FunctionalInterface
public interface CommandPartitionKeyResolver {
	CommandPartitionKey resolve(Command command);
}
