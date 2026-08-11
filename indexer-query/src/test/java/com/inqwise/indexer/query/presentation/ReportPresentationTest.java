package com.inqwise.indexer.query.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class ReportPresentationTest {
	@Test
	void validatesAndDefensivelyCopiesSchemas() {
		JsonObject parameters = objectSchema().put(
			"properties",
			new JsonObject().put("limit", new JsonObject().put("type", "integer"))
		);
		ReportPresentation presentation = ReportPresentation.builder()
			.withName("stories")
			.withTitle("Stories")
			.withParametersSchema(parameters)
			.withResultSchema(objectSchema())
			.build();

		parameters.getJsonObject("properties").put("unsafe", true);
		presentation.getParametersSchema().put("unsafe", true);

		assertEquals(
			new JsonObject().put("type", "integer"),
			presentation.getParametersSchema()
				.getJsonObject("properties")
				.getJsonObject("limit")
		);
		assertEquals(null, presentation.getParametersSchema().getValue("unsafe"));
		assertEquals(
			presentation.toJson(),
			new ReportPresentation(presentation.toJson()).toJson()
		);
	}

	@Test
	void rejectsNonObjectAndUnversionedSchemas() {
		assertThrows(IllegalArgumentException.class, () -> ReportPresentation.builder()
			.withName("unsafe/name")
			.withTitle("Stories")
			.withParametersSchema(objectSchema())
			.withResultSchema(objectSchema())
			.build());
		assertThrows(IllegalArgumentException.class, () -> ReportPresentation.builder()
			.withName("stories")
			.withTitle("Stories")
			.withParametersSchema(new JsonObject().put("type", "object"))
			.withResultSchema(objectSchema())
			.build());
		assertThrows(IllegalArgumentException.class, () -> ReportPresentation.builder()
			.withName("stories")
			.withTitle("Stories")
			.withParametersSchema(objectSchema())
			.withResultSchema(objectSchema().put("type", "array"))
			.build());
	}

	private JsonObject objectSchema() {
		return new JsonObject()
			.put("$schema", ReportPresentation.JSON_SCHEMA_DIALECT)
			.put("type", "object");
	}
}
