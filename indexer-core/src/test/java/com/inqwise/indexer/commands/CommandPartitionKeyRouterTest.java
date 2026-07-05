package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.inqwise.indexer.IndexerActionType;
import com.inqwise.indexer.PutDocumentActionItem;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class CommandPartitionKeyRouterTest {
	private final CommandPartitionKeyRouter router = CommandPartitionKeyRouter.withCoreResolvers();

	@Test
	void resolvesCoreResourceIdentities() {
		assertKey(CreateIndexerCommand.TYPE, "target_id", 12, "target:12");
		assertKey(DeleteIndexerCommand.TYPE, "indexer_id", 31, "indexer:31");
		assertKey(ResetIndexerQueueCommand.TYPE, "indexer_id", 31, "indexer:31");
		assertKey(CleanupResetIndexerQueueCommand.TYPE, "indexer_id", 31, "indexer:31");
		assertKey(CleanupDeletingIndexerCommand.TYPE, "indexer_id", 31, "indexer:31");
	}

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
	void resolvesConcreteActionsBySharedTargetBeforeIndexer() {
		SubmitIndexActionsCommand command = new SubmitIndexActionsCommand(List.of(
			action(12, 31, "1"),
			action(12, 32, "2")
		));

		assertEquals("target:12", router.resolve(command).value());
	}

	@Test
	void resolvesConcreteActionsWithoutTargetBySharedIndexer() {
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
		assertEquals("Concrete action batch must reference one target id", error.getMessage());
	}

	@Test
	void rejectsTargetAndIndexerOnlyMixedBatch() {
		GenericCommand command = actionsCommand(
			action(12, 31, "1"),
			action(null, 31, "2")
		);

		CommandFailure error = assertThrows(CommandFailure.class, () -> router.resolve(command));

		assertEquals(CommandFailureKind.FINAL, error.kind());
		assertEquals("Concrete action batch must reference one target id", error.getMessage());
	}

	@Test
	void rejectsUnknownCommandType() {
		CommandFailure error = assertThrows(CommandFailure.class, () -> router.resolve(
			new GenericCommand("extension.unknown", new JsonObject())
		));

		assertEquals(CommandFailureKind.FINAL, error.kind());
		assertEquals(
			"No command partition-key resolver for type: extension.unknown",
			error.getMessage()
		);
	}

	@Test
	void rejectsDuplicateResolverRegistration() {
		CommandPartitionKeyRouter custom = new CommandPartitionKeyRouter()
			.register("extension.command", command -> new CommandPartitionKey("extension:1"));

		assertThrows(IllegalArgumentException.class, () -> custom.register(
			"extension.command",
			command -> new CommandPartitionKey("extension:2")
		));
	}

	private void assertKey(
		String commandType,
		String field,
		Object identity,
		String expected
	) {
		GenericCommand command = new GenericCommand(
			commandType,
			new JsonObject().put(field, identity)
		);
		assertEquals(expected, router.resolve(command).value());
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
		return new PutDocumentActionItem(new JsonObject()
			.put(PutDocumentActionItem.TYPE, IndexerActionType.PUT_DOCUMENT.name())
			.put(PutDocumentActionItem.TARGET_ID, targetId)
			.put(PutDocumentActionItem.INDEXER_ID, indexerId)
			.put(PutDocumentActionItem.INDEX_NAME, indexerId == null ? null : "customers")
			.put(PutDocumentActionItem.UID, uid)
			.put(PutDocumentActionItem.DOCUMENT, new JsonObject().put("name", uid)));
	}
}
