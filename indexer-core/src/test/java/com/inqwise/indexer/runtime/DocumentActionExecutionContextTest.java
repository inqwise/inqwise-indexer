package com.inqwise.indexer.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.actions.IndexerActionType;

import io.vertx.core.json.JsonObject;

class DocumentActionExecutionContextTest {
	@Test
	void requiresActionIdentity() {
		DocumentActionExecutionContext.Builder context = DocumentActionExecutionContext.builder()
			.withIndexName("stories-v1")
			.withDocumentUid("42")
			.withActionType(IndexerActionType.REMOVE_DOCUMENT);

		assertThrows(NullPointerException.class, context::build);
		assertThrows(
			NullPointerException.class,
			() -> DocumentActionExecutionContext.builder()
				.withTargetId(1)
				.withIndexName("stories-v1")
				.withDocumentUid("42")
				.withActionType(IndexerActionType.REMOVE_DOCUMENT)
				.build()
		);
	}

	@Test
	void defensivelyCopiesPutDocument() {
		JsonObject document = new JsonObject().put("id", 42);
		DocumentActionExecutionContext context = DocumentActionExecutionContext.builder()
			.withTargetId(1)
			.withIndexerId(2)
			.withIndexName("stories-v1")
			.withDocumentUid("42")
			.withActionType(IndexerActionType.PUT_DOCUMENT)
			.withDocument(document)
			.build();

		document.put("id", 99);
		JsonObject firstRead = context.document();
		JsonObject secondRead = context.document();

		assertEquals(42, firstRead.getInteger("id"));
		assertNotSame(firstRead, secondRead);
	}
}
