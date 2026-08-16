package com.inqwise.indexer.query.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.vertx.core.json.JsonObject;

class ReportPresentationTest {
	@Test
	void jsonConstructorRejectsNullPayload() {
		NullPointerException error = assertThrows(
			NullPointerException.class,
			() -> new ReportPresentation(null)
		);

		assertEquals("json", error.getMessage());
	}

	@Test
	void jsonConstructorValidatesFields() {
		IllegalArgumentException nameError = assertThrows(
			IllegalArgumentException.class,
			() -> new ReportPresentation(validPresentationJson().put("name", "unsafe/name"))
		);
		IllegalArgumentException schemaError = assertThrows(
			IllegalArgumentException.class,
			() -> new ReportPresentation(validPresentationJson()
				.put("parameters_schema", new JsonObject().put("type", "object")))
		);

		assertEquals("name is not HTTP-path compatible", nameError.getMessage());
		assertEquals(
			"parametersSchema must use JSON Schema draft 2020-12",
			schemaError.getMessage()
		);
	}

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

	private JsonObject validPresentationJson() {
		return new JsonObject()
			.put("name", "stories")
			.put("title", "Stories")
			.put("parameters_schema", objectSchema())
			.put("result_schema", objectSchema());
	}
}
