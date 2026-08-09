package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class ReportExecutionRequestTest {
	@Test
	void callerIdentityIsNotPartOfTheTransportRequest() {
		ReportExecutionRequest request = new ReportExecutionRequest(new JsonObject()
			.put("report_name", "stories")
			.put("parameters", new JsonObject().put("limit", 5))
			.put("caller", new JsonObject()
				.put("consumer_name", "spoofed-consumer")
				.put("trusted_attributes", new JsonObject().put("tenant", "other"))));

		assertEquals("stories", request.getReportName());
		assertEquals(5, request.getParameters().getInteger("limit"));
		assertFalse(request.toJson().containsKey("caller"));
	}
}
