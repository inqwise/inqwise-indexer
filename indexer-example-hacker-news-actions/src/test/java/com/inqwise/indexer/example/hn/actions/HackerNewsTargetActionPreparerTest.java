package com.inqwise.indexer.example.hn.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.RemoveDocumentActionItem;
import com.inqwise.indexer.example.hn.model.HackerNewsDocument;
import com.inqwise.indexer.example.hn.model.HackerNewsDocumentCodec;
import com.inqwise.indexer.service.action.InvalidTargetActionPreparationException;

import io.vertx.core.json.JsonObject;

class HackerNewsTargetActionPreparerTest {
	private final HackerNewsTargetActionPreparer preparer =
		new HackerNewsTargetActionPreparer();

	@Test
	void validatesAndCanonicalizesPutActions() {
		JsonObject submitted = document().put("store_field", "discarded");

		PutDocumentActionItem prepared = assertInstanceOf(
			PutDocumentActionItem.class,
			preparer.prepare(List.of(IndexerActionItems.putDocument("42", submitted)))
				.result().getFirst()
		);
		assertEquals("A useful tool", prepared.getDocument().getString("title"));
		assertFalse(prepared.getDocument().containsKey("store_field"));
	}

	@Test
	void validatesRemoveIdentifiers() {
		RemoveDocumentActionItem prepared = assertInstanceOf(
			RemoveDocumentActionItem.class,
			preparer.prepare(List.of(IndexerActionItems.removeDocument("42")))
				.result().getFirst()
		);
		assertEquals("42", prepared.getUid());
	}

	@Test
	void rejectsMalformedDocumentsAndMismatchedIds() {
		assertThrows(
			InvalidTargetActionPreparationException.class,
			() -> preparer.prepare(List.of(IndexerActionItems.putDocument(
				"42",
				document().put("score", "high")
			)))
		);
		assertThrows(
			InvalidTargetActionPreparationException.class,
			() -> preparer.prepare(List.of(IndexerActionItems.putDocument(
				"43",
				document()
			)))
		);
	}

	private JsonObject document() {
		return new HackerNewsDocumentCodec().encode(HackerNewsDocument.builder()
			.withId(42)
			.withType("story")
			.withAuthor("ada")
			.withTime(1_700_000_000L)
			.withTitle("A useful tool")
			.withScore(17)
			.build());
	}
}
