package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;

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
		CompleteIndexActionItem item = CompleteIndexActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.build();

		IndexerActionItem parsed = IndexerActionItem.fromJson(item.toJson());

		assertInstanceOf(CompleteIndexActionItem.class, parsed);
		assertEquals(IndexerActionType.COMPLETE, parsed.getActionType());
		assertEquals(10, ((CompleteIndexActionItem) parsed).getTargetId());
		assertEquals(20, ((CompleteIndexActionItem) parsed).getIndexerId());
		assertEquals(item.toJson(), parsed.toJson());
	}

	@Test
	void catchUpBarrierActionRoundTripsThroughJson() {
		Instant barrierTimestamp = Instant.parse("2026-05-28T10:30:00Z");
		CatchUpBarrierActionItem item = CatchUpBarrierActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.withBarrierId("barrier-1")
			.withBarrierTimestamp(barrierTimestamp)
			.build();

		IndexerActionItem parsed = IndexerActionItem.fromJson(item.toJson());

		assertInstanceOf(CatchUpBarrierActionItem.class, parsed);
		assertEquals(IndexerActionType.CATCH_UP_BARRIER, parsed.getActionType());
		assertEquals(10, ((CatchUpBarrierActionItem) parsed).getTargetId());
		assertEquals(20, ((CatchUpBarrierActionItem) parsed).getIndexerId());
		assertEquals("barrier-1", ((CatchUpBarrierActionItem) parsed).getBarrierId());
		assertEquals(barrierTimestamp, ((CatchUpBarrierActionItem) parsed).getBarrierTimestamp());
		assertEquals(item.toJson(), parsed.toJson());
	}

	@Test
	void putActionRoundTripsConcreteIdentityFields() {
		PutDocumentActionItem item = PutDocumentActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.withIndexName("customers-2024-a")
			.withUid("42")
			.withSequence(100L)
			.withMutationId("mutation-1")
			.withDocument(new JsonObject().put("name", "Ada"))
			.build();

		IndexerActionItem parsed = IndexerActionItem.fromJson(item.toJson());

		assertInstanceOf(PutDocumentActionItem.class, parsed);
		assertEquals(item.toJson(), parsed.toJson());
	}

	@Test
	void removeActionRoundTripsConcreteIdentityFields() {
		RemoveDocumentActionItem item = RemoveDocumentActionItem.builder()
			.withTargetId(10)
			.withIndexerId(20)
			.withTargetName("customers-2024")
			.withIndexName("customers-2024-a")
			.withUid("42")
			.withSequence(100L)
			.withMutationId("mutation-1")
			.build();

		IndexerActionItem parsed = IndexerActionItem.fromJson(item.toJson());

		assertInstanceOf(RemoveDocumentActionItem.class, parsed);
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
