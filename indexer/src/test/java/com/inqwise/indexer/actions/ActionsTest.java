package com.inqwise.indexer.actions;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.catalog.indexers.IndexerModel;
import com.inqwise.indexer.catalog.indexers.IndexerRole;

import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ActionsTest {
	@Test
	void documentActionBuildersExposeOnlyLogicalPayloadFields() {
		assertFalse(hasPublicMethod(PutDocumentActionItem.Builder.class, "withTargetId"));
		assertFalse(hasPublicMethod(PutDocumentActionItem.Builder.class, "withIndexerId"));
		assertFalse(hasPublicMethod(PutDocumentActionItem.Builder.class, "withIndexName"));
		assertFalse(hasPublicMethod(RemoveDocumentActionItem.Builder.class, "withTargetId"));
		assertFalse(hasPublicMethod(RemoveDocumentActionItem.Builder.class, "withIndexerId"));
		assertFalse(hasPublicMethod(RemoveDocumentActionItem.Builder.class, "withIndexName"));
		assertFalse(hasPublicJsonConstructor(PutDocumentActionItem.class));
		assertFalse(hasPublicJsonConstructor(RemoveDocumentActionItem.class));
	}

	@Test
	void internalMarkerActionsExposeOnlyBuilderAndParserConstructionApi() {
		assertEquals(0, CompleteIndexActionItem.class.getConstructors().length);
		assertEquals(0, CatchUpBarrierActionItem.class.getConstructors().length);
	}

	private boolean hasPublicMethod(Class<?> type, String name) {
		return Arrays.stream(type.getMethods()).anyMatch(method -> method.getName().equals(name));
	}

	private boolean hasPublicJsonConstructor(Class<?> type) {
		return Arrays.stream(type.getConstructors())
			.map(Constructor::getParameterTypes)
			.anyMatch(parameters -> parameters.length == 1 && parameters[0].equals(JsonObject.class));
	}

	@Test
	void resolvesDocumentPutActionProvider() {
		var provider = Actions.getProvider(IndexerActionType.PUT_DOCUMENT);

		assertEquals(IndexerActionType.PUT_DOCUMENT, provider.type());
		assertNotNull(provider.action());
		assertNotNull(provider.router());
	}

	@Test
	void resolvesDocumentRemoveActionProvider() {
		var provider = Actions.getProvider(IndexerActionType.REMOVE_DOCUMENT);

		assertEquals(IndexerActionType.REMOVE_DOCUMENT, provider.type());
		assertNotNull(provider.action());
		assertNotNull(provider.router());
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
		PutDocumentActionItem item = IndexerActionItems.concretePutDocument(
			10,
			20,
			"customers-2024-a",
			"42",
			new JsonObject().put("name", "Ada")
		);

		IndexerActionItem parsed = IndexerActionItem.fromJson(item.toJson());

		assertInstanceOf(PutDocumentActionItem.class, parsed);
		assertEquals(item.toJson(), parsed.toJson());
	}

	@Test
	void putActionOmitsEmptyConcreteIdentityFields() {
		PutDocumentActionItem item = IndexerActionItems.putDocument(
			"42",
			new JsonObject().put("name", "Ada")
		);

		JsonObject json = item.toJson();

		assertFalse(json.containsKey(PutDocumentActionItem.TARGET_ID));
		assertFalse(json.containsKey(PutDocumentActionItem.INDEXER_ID));
		assertFalse(json.containsKey(PutDocumentActionItem.INDEX_NAME));
	}

	@Test
	void putActionBuilderSnapshotsDocumentOnInput() {
		JsonObject document = new JsonObject().put("name", "Ada");
		PutDocumentActionItem.Builder builder = PutDocumentActionItem.builder()
			.withUid("42")
			.withDocument(document);

		document.put("name", "Grace");

		assertEquals("Ada", builder.build().getDocument().getString("name"));
	}

	@Test
	void removeActionRoundTripsConcreteIdentityFields() {
		RemoveDocumentActionItem item = IndexerActionItems.concreteRemoveDocument(
			10,
			20,
			"customers-2024-a",
			"42"
		);

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
		PutDocumentActionItem item = IndexerActionItems.concretePutDocument(
			10,
			20,
			"customers_1",
			"42",
			new JsonObject().put("name", "Ada")
		);

		Actions.getProvider(IndexerActionType.PUT_DOCUMENT)
			.action()
			.process(model, store, item)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals("Ada", store.get("customers_1", "42").getString("name"));
				testContext.completeNow();
			})));
	}

	@Test
	void documentPutRouterCreatesConcreteAction() {
		PutDocumentActionItem item = IndexerActionItems.putDocument(
			"42",
			new JsonObject().put("name", "Ada")
		);

		IndexerActionItem routed = Actions.getProvider(IndexerActionType.PUT_DOCUMENT)
			.router()
			.route(routeContext(), item, IndexerActionRouteMode.DIRECT)
			.orElseThrow();

		PutDocumentActionItem put = (PutDocumentActionItem) routed;
		assertEquals(10, put.getTargetId());
		assertEquals(20, put.getIndexerId());
		assertEquals("customers-2024-a", put.getIndexName());
		assertEquals("42", put.getUid());
		assertEquals("Ada", put.getDocument().getString("name"));
	}

	@Test
	void documentRemoveRouterSkipsCandidateMismatch() {
		RemoveDocumentActionItem item = IndexerActionItems.concreteRemoveDocument(
			10,
			21,
			"customers-2024-a",
			"42"
		);

		var routed = Actions.getProvider(IndexerActionType.REMOVE_DOCUMENT)
			.router()
			.route(routeContext(), item, IndexerActionRouteMode.CANDIDATE);

		assertEquals(true, routed.isEmpty());
	}

	private IndexerActionRouteContext routeContext() {
		return new IndexerActionRouteContext(
			10,
			20,
			"customers",
			"customers-2024-a",
			"queue-customers",
			IndexerRole.LIVE_WRITER
		);
	}
}
