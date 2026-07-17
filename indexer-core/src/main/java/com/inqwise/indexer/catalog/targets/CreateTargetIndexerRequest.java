package com.inqwise.indexer.catalog.targets;

import java.util.Objects;

import com.inqwise.indexer.provisioning.DocumentIndexNameValidator;

public record CreateTargetIndexerRequest(
	String prefix,
	String indexName,
	String queueName,
	InitialPublicationMode initialPublicationMode
) {
	public CreateTargetIndexerRequest {
		prefix = requireNonBlank(prefix, "prefix");
		indexName = DocumentIndexNameValidator.requireConcrete(indexName);
		queueName = requireNonBlank(queueName, "queueName");
		Objects.requireNonNull(initialPublicationMode, "initialPublicationMode");
	}

	private static String requireNonBlank(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		return value;
	}
}
