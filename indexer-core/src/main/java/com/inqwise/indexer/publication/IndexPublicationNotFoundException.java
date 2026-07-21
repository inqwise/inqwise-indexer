package com.inqwise.indexer.publication;

import java.util.Objects;

public final class IndexPublicationNotFoundException extends RuntimeException {
	public enum Lookup {
		PUBLICATION_ID,
		INDEXER_ID,
		PUBLICATION_BY_INDEXER_ID
	}

	private final Lookup lookup;
	private final Integer lookupId;

	private IndexPublicationNotFoundException(Lookup lookup, Integer lookupId, String message) {
		super(message);
		this.lookup = Objects.requireNonNull(lookup, "lookup");
		this.lookupId = Objects.requireNonNull(lookupId, "lookupId");
	}

	public static IndexPublicationNotFoundException publication(Integer publicationId) {
		return new IndexPublicationNotFoundException(
			Lookup.PUBLICATION_ID,
			publicationId,
			"Publication not found: " + publicationId
		);
	}

	public static IndexPublicationNotFoundException indexer(Integer indexerId) {
		return new IndexPublicationNotFoundException(
			Lookup.INDEXER_ID,
			indexerId,
			"Indexer not found: " + indexerId
		);
	}

	public static IndexPublicationNotFoundException publicationByIndexer(Integer indexerId) {
		return new IndexPublicationNotFoundException(
			Lookup.PUBLICATION_BY_INDEXER_ID,
			indexerId,
			"Publication not found for indexer: " + indexerId
		);
	}

	public Lookup lookup() {
		return lookup;
	}

	public Integer lookupId() {
		return lookupId;
	}
}
