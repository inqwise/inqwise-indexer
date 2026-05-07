package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ActionsTest {
	@Test
	void resolvesDocumentPutActionProvider() {
		var provider = Actions.getProvider(IndexerActionType.PUT_DOCUMENT);

		assertEquals(IndexerActionType.PUT_DOCUMENT, provider.type());
		assertNotNull(provider.action());
	}
}
