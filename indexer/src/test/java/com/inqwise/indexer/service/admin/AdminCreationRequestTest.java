package com.inqwise.indexer.service.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AdminCreationRequestTest {
	@Test
	void indexerCreationDoesNotExposePublicationState() {
		assertFalse(new AdminCreateIndexerRequest().toJson().containsKey("publication_state"));
	}

	@Test
	void nestedTargetIndexerCreationDoesNotExposePublicationState() {
		assertFalse(new AdminCreateTargetIndexerRequest().toJson().containsKey("publication_state"));
	}
}
