package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ActionsTest {
	@Test
	void resolvesDocumentPutActionProvider() {
		var provider = Actions.getProvider(IndexerActionType.PUT_DOCUMENT);

		assertEquals(IndexerActionType.PUT_DOCUMENT, provider.type());
		assertNotNull(provider.action());
	}

	@Test
	void completeActionRoundTripsThroughJson() {
		CompleteIndexActionItem item = new CompleteIndexActionItem();

		IndexerActionItem parsed = IndexerActionItem.fromJson(item.toJson());

		assertInstanceOf(CompleteIndexActionItem.class, parsed);
		assertEquals(IndexerActionType.COMPLETE, parsed.getActionType());
		assertEquals(item.toJson(), parsed.toJson());
	}

	@Test
	void documentPutActionWritesToDocumentStore(VertxTestContext testContext) {
		InMemoryIndexerDocumentStore store = new InMemoryIndexerDocumentStore();
		IndexerModel model = IndexerModel.builder()
			.withTargetName("customers")
			.withIndexName("customers_1")
			.build();
		PutDocumentActionItem item = PutDocumentActionItem.builder()
			.withIndexName("customers_1")
			.withUid("42")
			.withDocument(new JsonObject().put("name", "Ada"))
			.build();

		Actions.getProvider(IndexerActionType.PUT_DOCUMENT)
			.action()
			.process(model, store, item)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("Ada", store.get("customers_1", "42").getString("name"));
				testContext.completeNow();
			})));
	}
}
