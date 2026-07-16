package com.inqwise.indexer.service.admin;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class AdminCreationRequestTest {
	@Test
	void indexerCreationDoesNotExposePublicationState() {
		var json = new AdminCreateIndexerRequest().toJson();

		assertFalse(json.containsKey("publication_state"));
		assertFalse(json.containsKey("mutation_state"));
		assertFalse(json.containsKey("indexer_type"));
	}

	@Test
	void nestedTargetIndexerCreationDoesNotExposePublicationState() {
		var json = new AdminCreateTargetIndexerRequest().toJson();

		assertFalse(json.containsKey("publication_state"));
		assertFalse(json.containsKey("mutation_state"));
		assertFalse(json.containsKey("indexer_type"));
	}
}
