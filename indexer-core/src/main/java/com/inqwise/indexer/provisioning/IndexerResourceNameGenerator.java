package com.inqwise.indexer.provisioning;

import java.util.UUID;

import com.inqwise.indexer.catalog.targets.TargetNameValidator;

public final class IndexerResourceNameGenerator {
	private IndexerResourceNameGenerator() {
	}

	public static String targetPrefix() {
		return generatedPrefix('t');
	}

	public static String indexerPrefix() {
		return generatedPrefix('i');
	}

	public static GeneratedIndexerResources forTarget(String targetName) {
		TargetNameValidator.requireTargetName(targetName);
		String suffix = UUID.randomUUID().toString();
		String indexName = targetName + "--idx-" + suffix;
		String queueName = targetName + "--queue-" + suffix;
		TargetNameValidator.requireGeneratedResourceName(indexName);
		TargetNameValidator.requireGeneratedResourceName(queueName);
		return new GeneratedIndexerResources(
			generatedPrefix('i', suffix),
			indexName,
			queueName
		);
	}

	private static String generatedPrefix(char type) {
		return generatedPrefix(type, UUID.randomUUID().toString());
	}

	private static String generatedPrefix(char type, String suffix) {
		return type + suffix.replace("-", "").substring(0, 12);
	}
}
