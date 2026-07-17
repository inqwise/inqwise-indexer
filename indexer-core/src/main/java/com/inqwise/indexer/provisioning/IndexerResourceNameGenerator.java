package com.inqwise.indexer.provisioning;

import java.util.UUID;

import com.inqwise.indexer.catalog.targets.TargetNameValidator;

public final class IndexerResourceNameGenerator {
	private IndexerResourceNameGenerator() {
	}

	public static GeneratedIndexerResources forTarget(String targetName) {
		TargetNameValidator.requireTargetName(targetName);
		String suffix = UUID.randomUUID().toString();
		String indexName = targetName + "--idx-" + suffix;
		String queueName = targetName + "--queue-" + suffix;
		TargetNameValidator.requireGeneratedResourceName(indexName);
		TargetNameValidator.requireGeneratedResourceName(queueName);
		return new GeneratedIndexerResources(
			generatedPrefix(suffix),
			indexName,
			queueName
		);
	}

	private static String generatedPrefix(String suffix) {
		return "i" + suffix.replace("-", "").substring(0, 12);
	}
}
