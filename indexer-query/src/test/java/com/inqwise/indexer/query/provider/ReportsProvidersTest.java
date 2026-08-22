package com.inqwise.indexer.query.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.query.DocumentQueryResults;
import com.inqwise.indexer.query.ReportCatalog;
import com.inqwise.indexer.query.ReportDefinition;
import com.inqwise.indexer.query.ReportDescriptor;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.ReportNotFoundException;
import com.inqwise.indexer.query.ReportQueryPlan;
import com.inqwise.indexer.query.ReportQueryScope;
import com.inqwise.indexer.query.ReportRequestCodec;
import com.inqwise.indexer.query.ReportResultCodec;
import com.inqwise.indexer.query.presentation.ReportPresentation;
import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportExecutionResult;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

class ReportsProvidersTest {
	@Test
	void combinesCatalogsAndDispatchesToOwningProvider() {
		ReportsProviders providers = ReportsProviders.create(
			List.of(factory("first", "report.first"), factory("second", "report.second")),
			context()
		);

		assertEquals(
			List.of("report.first", "report.second"),
			providers.catalog().descriptors().stream()
				.map(ReportDescriptor::name)
				.toList()
		);
		ReportExecutionResult result = providers.service().execute(
			ReportExecutionRequest.builder()
				.withReportName("report.second")
				.build()
		).toCompletionStage().toCompletableFuture().join();
		assertEquals("second", result.getPayload().getString("provider"));
	}

	@Test
	void rejectsDuplicateProviderIdsAndReportNames() {
		assertThrows(
			IllegalArgumentException.class,
			() -> ReportsProviders.create(
				List.of(factory("same", "report.first"), factory("same", "report.second")),
				context()
			)
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> ReportsProviders.create(
				List.of(factory("first", "report.same"), factory("second", "report.same")),
				context()
			)
		);
	}

	@Test
	void reportsUnknownNamesWithDomainError() {
		ReportsProviders providers = ReportsProviders.create(List.of(), context());

		Throwable error = assertThrows(
			Exception.class,
			() -> providers.service().execute(
				ReportExecutionRequest.builder()
					.withReportName("missing")
					.build()
			).toCompletionStage().toCompletableFuture().join()
		);
		assertInstanceOf(ReportNotFoundException.class, error.getCause());
	}

	private static ReportsProviderFactory factory(String id, String reportName) {
		return new ReportsProviderFactory() {
			@Override
			public String id() {
				return id;
			}

			@Override
			public ReportsProvider create(ReportsProviderContext context) {
				return DefaultReportsProvider.builder()
					.withCatalog(catalog(reportName))
					.withService(request -> Future.succeededFuture(
						ReportExecutionResult.builder()
							.withPayload(new JsonObject().put("provider", id))
							.build()
					))
					.build();
			}
		};
	}

	private static ReportsProviderContext context() {
		return ReportsProviderContext.builder()
			.withPublishedIndexes(query -> Future.succeededFuture(List.of()))
			.withDocuments(indexName -> Map.of())
			.build();
	}

	private static ReportCatalog catalog(String reportName) {
		ReportDefinition<Object, Object> definition = new ReportDefinition<>() {
			@Override
			public ReportDescriptor descriptor() {
				return ReportDescriptor.builder()
					.withName(reportName)
					.withTargetName("test-target")
					.withScope(ReportQueryScope.builder().build())
					.build();
			}

			@Override
			public ReportRequestCodec<Object> requestCodec() {
				throw new UnsupportedOperationException();
			}

			@Override
			public ReportResultCodec<Object> resultCodec() {
				throw new UnsupportedOperationException();
			}

			@Override
			public ReportQueryPlan plan(
				Object request,
				ReportExecutionContext context
			) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Object decode(DocumentQueryResults results) {
				throw new UnsupportedOperationException();
			}
		};
		return new ReportCatalog() {
			@Override
			public Optional<ReportDefinition<?, ?>> find(String name) {
				return reportName.equals(name) ? Optional.of(definition) : Optional.empty();
			}

			@Override
			public Collection<ReportDescriptor> descriptors() {
				return List.of(definition.descriptor());
			}

			@Override
			public Collection<ReportPresentation> presentations() {
				return List.of();
			}
		};
	}
}
