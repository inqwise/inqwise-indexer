package com.inqwise.indexer.publication;

import java.util.Objects;

public final class IndexPublicationConflictException extends RuntimeException {
	public enum Resource {
		PUBLICATION("Publication"),
		INDEXER("Indexer publication");

		private final String label;

		Resource(String label) {
			this.label = label;
		}
	}

	private final Resource resource;
	private final Integer resourceId;

	private IndexPublicationConflictException(
		Resource resource,
		Integer resourceId,
		String details
	) {
		super(Objects.requireNonNull(resource, "resource").label + " conflict for id "
			+ Objects.requireNonNull(resourceId, "resourceId") + ": "
			+ Objects.requireNonNull(details, "details"));
		this.resource = resource;
		this.resourceId = resourceId;
	}

	public static IndexPublicationConflictException publication(
		Integer publicationId,
		String details
	) {
		return new IndexPublicationConflictException(Resource.PUBLICATION, publicationId, details);
	}

	public static IndexPublicationConflictException indexer(Integer indexerId, String details) {
		return new IndexPublicationConflictException(Resource.INDEXER, indexerId, details);
	}

	public Resource resource() {
		return resource;
	}

	public Integer resourceId() {
		return resourceId;
	}
}
