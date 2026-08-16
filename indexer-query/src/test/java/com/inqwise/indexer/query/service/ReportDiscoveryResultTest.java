package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.query.presentation.ReportPresentation;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ReportDiscoveryResultTest {
	@Test
	void jsonConstructorRejectsNullPayload() {
		NullPointerException error = assertThrows(
			NullPointerException.class,
			() -> new ReportDiscoveryResult(null)
		);

		assertEquals("json", error.getMessage());
	}

	@Test
	void jsonConstructorValidatesContainedPresentations() {
		IllegalArgumentException error = assertThrows(
			IllegalArgumentException.class,
			() -> new ReportDiscoveryResult(new JsonObject()
				.put("reports", new JsonArray()
					.add(presentationJson().put("name", "unsafe/name"))))
		);

		assertEquals("name is not HTTP-path compatible", error.getMessage());
	}

	@Test
	void defensivelyCopiesPresentations() {
		ReportPresentation presentation = presentation("stories", "Stories");
		ReportDiscoveryResult result = ReportDiscoveryResult.builder()
			.withReports(List.of(presentation))
			.build();

		presentation.setTitle("Changed");
		result.getReports().getFirst().setTitle("Also changed");

		assertEquals("Stories", result.getReports().getFirst().getTitle());
		assertEquals(
			result.toJson(),
			new ReportDiscoveryResult(result.toJson()).toJson()
		);
	}

	private ReportPresentation presentation(String name, String title) {
		return ReportPresentation.builder()
			.withName(name)
			.withTitle(title)
			.withParametersSchema(objectSchema())
			.withResultSchema(objectSchema())
			.build();
	}

	private JsonObject presentationJson() {
		return presentation("stories", "Stories").toJson();
	}

	private JsonObject objectSchema() {
		return new JsonObject()
			.put("$schema", ReportPresentation.JSON_SCHEMA_DIALECT)
			.put("type", "object");
	}
}
