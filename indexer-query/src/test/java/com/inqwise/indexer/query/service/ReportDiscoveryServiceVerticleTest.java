package com.inqwise.indexer.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.query.ReportCatalog;
import com.inqwise.indexer.query.ReportDefinition;
import com.inqwise.indexer.query.ReportDescriptor;
import com.inqwise.indexer.query.presentation.ReportPresentation;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class ReportDiscoveryServiceVerticleTest {
	@Test
	void discoversSortedPresentationsThroughGeneratedProxy(
		Vertx vertx,
		VertxTestContext testContext
	) {
		String address = ReportDiscoveryServices.address("test");
		ReportCatalog catalog = catalog(List.of(
			presentation("z-report", "Last"),
			presentation("a-report", "First")
		));

		vertx.deployVerticle(new ReportDiscoveryServiceVerticle(catalog, address))
			.compose(deploymentId -> ReportDiscoveryServices.proxy(vertx, address)
				.discover()
				.compose(result -> vertx.undeploy(deploymentId).map(result)))
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(
					List.of("a-report", "z-report"),
					result.getReports().stream()
						.map(ReportPresentation::getName)
						.toList()
				);
				assertEquals("First", result.getReports().getFirst().getTitle());
				testContext.completeNow();
			})));
	}

	private ReportCatalog catalog(List<ReportPresentation> presentations) {
		return new ReportCatalog() {
			@Override
			public Optional<ReportDefinition<?, ?>> find(String reportName) {
				return Optional.empty();
			}

			@Override
			public Collection<ReportDescriptor> descriptors() {
				return List.of();
			}

			@Override
			public Collection<ReportPresentation> presentations() {
				return presentations;
			}
		};
	}

	private ReportPresentation presentation(String name, String title) {
		return ReportPresentation.builder()
			.withName(name)
			.withTitle(title)
			.withParametersSchema(objectSchema())
			.withResultSchema(objectSchema())
			.build();
	}

	private JsonObject objectSchema() {
		return new JsonObject()
			.put("$schema", ReportPresentation.JSON_SCHEMA_DIALECT)
			.put("type", "object");
	}
}
