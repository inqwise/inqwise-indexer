package com.inqwise.indexer.example.hn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.RemoveDocumentActionItem;
import com.inqwise.indexer.example.hn.model.HackerNewsDocument;
import com.inqwise.indexer.example.hn.model.HackerNewsDocumentCodec;

class HackerNewsDocumentProjectorTest {
	private final HackerNewsDocumentProjector projector = new HackerNewsDocumentProjector();

	@Test
	void projectsCurrentItemToStableDocument() {
		HackerNewsProjection projection = projector.project(HackerNewsItem.builder()
			.withId(42)
			.withType("story")
			.withBy("ada")
			.withTime(1_700_000_000L)
			.withTitle("A useful tool")
			.withUrl("https://example.test/tool")
			.withScore(17)
			.withDescendants(3)
			.withKids(List.of(43L, 44L))
			.build());

		PutDocumentActionItem put = assertInstanceOf(
			PutDocumentActionItem.class,
			projection.action()
		);
		assertEquals(42, projection.itemId());
		assertEquals("42", put.getUid());
		assertEquals("A useful tool", put.getDocument().getString("title"));
		assertEquals("hacker-news", put.getDocument().getString("source"));
		HackerNewsDocument decoded = new HackerNewsDocumentCodec().decode(put.getDocument());
		assertEquals(42, decoded.id());
		assertEquals(List.of(43L, 44L), decoded.kids());
	}

	@Test
	void projectsDeadItemToIdempotentRemoval() {
		HackerNewsProjection projection = projector.project(HackerNewsItem.builder()
			.withId(42)
			.withDead(true)
			.build());

		RemoveDocumentActionItem remove = assertInstanceOf(
			RemoveDocumentActionItem.class,
			projection.action()
		);
		assertEquals("42", remove.getUid());
		assertEquals("REMOVE", projection.fingerprint());
	}

	@Test
	void rejectsInvalidCurrentItemBeforeCreatingPutAction() {
		HackerNewsItem invalid = HackerNewsItem.builder()
			.withId(42)
			.withType("story")
			.withTime(1_700_000_000L)
			.build();

		assertThrows(IllegalArgumentException.class, () -> projector.project(invalid));
	}
}
