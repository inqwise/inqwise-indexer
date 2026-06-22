package com.inqwise.indexer.commands;

import java.util.Objects;

public record CommandPartitionKey(String value) {
	public CommandPartitionKey {
		Objects.requireNonNull(value, "value");
		if (value.isBlank()) {
			throw new IllegalArgumentException("Command partition key must not be blank");
		}
	}

	public static CommandPartitionKey targetName(String targetName) {
		return named("target-name", targetName, "targetName");
	}

	public static CommandPartitionKey target(Integer targetId) {
		return identified("target", targetId, "targetId");
	}

	public static CommandPartitionKey indexer(Integer indexerId) {
		return identified("indexer", indexerId, "indexerId");
	}

	public static CommandPartitionKey publication(Integer publicationId) {
		return identified("publication", publicationId, "publicationId");
	}

	private static CommandPartitionKey named(String namespace, String name, String field) {
		Objects.requireNonNull(name, field);
		if (name.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return new CommandPartitionKey(namespace + ":" + name);
	}

	private static CommandPartitionKey identified(String namespace, Integer id, String field) {
		Objects.requireNonNull(id, field);
		return new CommandPartitionKey(namespace + ":" + id);
	}
}
