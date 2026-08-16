package com.inqwise.indexer.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Instant;
import java.util.List;

import com.inqwise.indexer.actions.IndexerActionItems;
import com.inqwise.indexer.actions.PutDocumentActionItem;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class SubmitIndexActionsCommandTest {
	@Test
	void concreteCommandOmitsTargetEnvelopeFields() {
		SubmitIndexActionsCommand command = SubmitIndexActionsCommand.builder()
			.withActions(List.of(IndexerActionItems.concretePutDocument(
				10,
				20,
				"customers-2026-06",
				"42",
				new JsonObject().put("name", "Ada")
			)))
			.build();

		JsonObject json = command.toJson();

		assertFalse(json.containsKey("target_name"));
		assertFalse(json.containsKey("timestamp"));
		assertEquals(1, json.getJsonArray("actions").size());
	}

	@Test
	void targetEnvelopeCommandIncludesTargetEnvelopeFields() {
		Instant timestamp = Instant.parse("2026-06-25T10:15:00Z");
		SubmitIndexActionsCommand command = SubmitIndexActionsCommand.builder()
			.withTargetName("customers")
			.withTimestamp(timestamp)
			.withActions(List.of(IndexerActionItems.putDocument(
				"42",
				new JsonObject().put("name", "Ada")
			)))
			.build();

		JsonObject json = command.toJson();
		JsonObject action = json.getJsonArray("actions").getJsonObject(0);

		assertEquals("customers", json.getString("target_name"));
		assertEquals(timestamp.toString(), json.getString("timestamp"));
		assertFalse(action.containsKey(PutDocumentActionItem.TARGET_ID));
		assertFalse(action.containsKey(PutDocumentActionItem.INDEXER_ID));
		assertFalse(action.containsKey(PutDocumentActionItem.INDEX_NAME));
	}

	@Test
	void exposesOnlyBuilderAndParserConstructionApi() {
		assertEquals(0, SubmitIndexActionsCommand.class.getConstructors().length);
	}
}
