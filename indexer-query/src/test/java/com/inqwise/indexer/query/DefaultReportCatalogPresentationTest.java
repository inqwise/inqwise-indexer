package com.inqwise.indexer.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.query.presentation.ReportPresentation;

import io.vertx.core.json.JsonObject;

class DefaultReportCatalogPresentationTest {
	@Test
	void rejectsPresentationWithDifferentReportName() {
		assertThrows(IllegalArgumentException.class, () -> DefaultReportCatalog.builder()
			.withDefinitions(List.of(new TestDefinition("definition", "presentation")))
			.build());
	}

	@Test
	void returnsDefensivePresentationCopies() {
		DefaultReportCatalog catalog = DefaultReportCatalog.builder()
			.withDefinitions(List.of(new TestDefinition("report", "report")))
			.build();

		ReportPresentation first = catalog.presentations().iterator().next();
		first.setTitle("Changed");

		assertEquals(
			"Report",
			catalog.presentations().iterator().next().getTitle()
		);
	}

	private static final class TestDefinition
		implements PresentedReportDefinition<JsonObject, JsonObject> {
		private final ReportDescriptor descriptor;
		private final ReportPresentation presentation;

		private TestDefinition(String definitionName, String presentationName) {
			descriptor = ReportDescriptor.builder()
				.withName(definitionName)
				.withTargetName("customers")
				.build();
			presentation = ReportPresentation.builder()
				.withName(presentationName)
				.withTitle("Report")
				.withParametersSchema(objectSchema())
				.withResultSchema(objectSchema())
				.build();
		}

		@Override
		public ReportDescriptor descriptor() {
			return descriptor;
		}

		@Override
		public ReportPresentation presentation() {
			return new ReportPresentation(presentation.toJson());
		}

		@Override
		public ReportRequestCodec<JsonObject> requestCodec() {
			return new ReportRequestCodec<>() {
				@Override
				public JsonObject decode(JsonObject parameters) {
					return parameters.copy();
				}

				@Override
				public JsonObject encode(JsonObject request) {
					return request.copy();
				}
			};
		}

		@Override
		public ReportResultCodec<JsonObject> resultCodec() {
			return new ReportResultCodec<>() {
				@Override
				public JsonObject decode(JsonObject payload) {
					return payload.copy();
				}

				@Override
				public JsonObject encode(JsonObject result) {
					return result.copy();
				}
			};
		}

		@Override
		public ReportQueryPlan plan(
			JsonObject request,
			ReportExecutionContext context
		) {
			throw new UnsupportedOperationException();
		}

		@Override
		public JsonObject decode(DocumentQueryResults results) {
			throw new UnsupportedOperationException();
		}

		private static JsonObject objectSchema() {
			return new JsonObject()
				.put("$schema", ReportPresentation.JSON_SCHEMA_DIALECT)
				.put("type", "object");
		}
	}
}
