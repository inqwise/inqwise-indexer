package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class ReportExecutionRequestTest {
	@Test
	void jsonConstructorRejectsNullPayload() {
		NullPointerException error = assertThrows(
			NullPointerException.class,
			() -> new ReportExecutionRequest(null)
		);

		assertEquals("json", error.getMessage());
	}

	@Test
	void jsonConstructorRequiresReportName() {
		IllegalArgumentException missing = assertThrows(
			IllegalArgumentException.class,
			() -> new ReportExecutionRequest(new JsonObject()
				.put("parameters", new JsonObject()))
		);
		IllegalArgumentException blank = assertThrows(
			IllegalArgumentException.class,
			() -> new ReportExecutionRequest(new JsonObject()
				.put("report_name", " ")
				.put("parameters", new JsonObject()))
		);

		assertEquals("reportName must not be blank", missing.getMessage());
		assertEquals("reportName must not be blank", blank.getMessage());
	}

	@Test
	void rejectsNullParameters() {
		NullPointerException builderError = assertThrows(
			NullPointerException.class,
			() -> ReportExecutionRequest.builder()
				.withReportName("stories")
				.withParameters(null)
				.build()
		);
		NullPointerException setterError = assertThrows(
			NullPointerException.class,
			() -> new ReportExecutionRequest()
				.setParameters(null)
		);

		assertEquals("parameters", builderError.getMessage());
		assertEquals("parameters", setterError.getMessage());
	}

	@Test
	void jsonConstructorDefensivelyCopiesParameters() {
		JsonObject parameters = new JsonObject().put("limit", 5);
		ReportExecutionRequest request = new ReportExecutionRequest(new JsonObject()
			.put("report_name", "stories")
			.put("parameters", parameters));

		parameters.put("limit", 10);
		request.getParameters().put("limit", 15);

		assertEquals(5, request.getParameters().getInteger("limit"));
	}

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
