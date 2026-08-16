package com.inqwise.indexer.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.IndexerActionType;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.commands.CommandFailureKind;
import com.inqwise.indexer.commands.CommandPartitionKeyRouter;
import com.inqwise.indexer.commands.GenericCommand;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RoutingCommandPartitionKeyResolversTest {
	private final CommandPartitionKeyRouter router = createRouter();

	@Test
	void resolvesLogicalActionsByTargetName() {
		PutDocumentActionItem action = action(null, null, "1");
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(
			"customers",
			null,
			List.of(action)
		);

		assertEquals("target-name:customers", router.resolve(command).value());
	}

	@Test
	void rejectsBlankTargetName() {
		GenericCommand command = new GenericCommand(
			SubmitIndexActionsCommand.TYPE,
			new JsonObject()
				.put("target_name", " ")
				.put("actions", new JsonArray(List.of(action(null, null, "1")).stream()
					.map(PutDocumentActionItem::toJson)
					.toList()))
		);

		CommandFailure error = assertThrows(CommandFailure.class, () -> router.resolve(command));

		assertEquals(CommandFailureKind.FINAL, error.kind());
		assertEquals("targetName must not be blank", error.getMessage());
	}

	@Test
	void resolvesRoutedActionsBySharedTargetBeforeIndexer() {
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(List.of(
			action(12, 31, "1"),
			action(12, 32, "2")
		));

		assertEquals("target:12", router.resolve(command).value());
	}

	@Test
	void resolvesRoutedActionsWithoutTargetBySharedIndexer() {
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(List.of(
			action(null, 31, "1"),
			action(null, 31, "2")
		));

		assertEquals("indexer:31", router.resolve(command).value());
	}

	@Test
	void rejectsMixedTargetBatch() {
		GenericCommand command = actionsCommand(
			action(12, 31, "1"),
			action(13, 32, "2")
		);

		CommandFailure error = assertThrows(CommandFailure.class, () -> router.resolve(command));

		assertEquals(CommandFailureKind.FINAL, error.kind());
		assertEquals("Routed action batch must reference one target id", error.getMessage());
	}

	@Test
	void rejectsTargetAndIndexerOnlyMixedBatch() {
		GenericCommand command = actionsCommand(
			action(12, 31, "1"),
			action(null, 31, "2")
		);

		CommandFailure error = assertThrows(CommandFailure.class, () -> router.resolve(command));

		assertEquals(CommandFailureKind.FINAL, error.kind());
		assertEquals("Routed action batch must reference one target id", error.getMessage());
	}

	private GenericCommand actionsCommand(PutDocumentActionItem... actions) {
		return new GenericCommand(
			SubmitIndexActionsCommand.TYPE,
			new JsonObject().put("actions", new JsonArray(List.of(actions).stream()
				.map(PutDocumentActionItem::toJson)
				.toList()))
		);
	}

	private PutDocumentActionItem action(Integer targetId, Integer indexerId, String uid) {
		return (PutDocumentActionItem) IndexerActionItem.fromJson(new JsonObject()
			.put(PutDocumentActionItem.TYPE, IndexerActionType.PUT_DOCUMENT.name())
			.put(PutDocumentActionItem.TARGET_ID, targetId)
			.put(PutDocumentActionItem.INDEXER_ID, indexerId)
			.put(PutDocumentActionItem.INDEX_NAME, indexerId == null ? null : "customers")
			.put(PutDocumentActionItem.UID, uid)
			.put(PutDocumentActionItem.DOCUMENT, new JsonObject().put("name", uid)));
	}

	private static CommandPartitionKeyRouter createRouter() {
		CommandPartitionKeyRouter router = new CommandPartitionKeyRouter();
		RoutingCommandPartitionKeyResolvers.registerWith(router);
		return router;
	}
}
