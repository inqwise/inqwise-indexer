package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class ReportExecutionResultTest {
	@Test
	void jsonConstructorRejectsNullPayload() {
		NullPointerException error = assertThrows(
			NullPointerException.class,
			() -> new ReportExecutionResult(null)
		);

		assertEquals("json", error.getMessage());
	}

	@Test
	void requiresExplicitPayload() {
		NullPointerException jsonError = assertThrows(
			NullPointerException.class,
			() -> new ReportExecutionResult(new JsonObject())
		);
		NullPointerException builderError = assertThrows(
			NullPointerException.class,
			() -> ReportExecutionResult.builder()
				.withPayload(null)
				.build()
		);
		NullPointerException setterError = assertThrows(
			NullPointerException.class,
			() -> new ReportExecutionResult()
				.setPayload(null)
		);

		assertEquals("payload", jsonError.getMessage());
		assertEquals("payload", builderError.getMessage());
		assertEquals("payload", setterError.getMessage());
	}

	@Test
	void defensivelyCopiesPayload() {
		JsonObject payload = new JsonObject().put("count", 5);
		ReportExecutionResult result = new ReportExecutionResult(new JsonObject()
			.put("payload", payload));

		payload.put("count", 10);
		result.getPayload().put("count", 15);

		assertEquals(5, result.getPayload().getInteger("count"));
	}
}
