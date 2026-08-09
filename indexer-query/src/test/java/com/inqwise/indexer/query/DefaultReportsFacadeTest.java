package com.inqwise.indexer.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.publication.PublishedIndexQuery;
import com.inqwise.indexer.publication.PublishedIndexResolver;
import com.inqwise.indexer.query.service.ReportExecutionRequest;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class DefaultReportsFacadeTest {
	private static final Instant JANUARY = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant JANUARY_15 = Instant.parse("2026-01-15T00:00:00Z");
	private static final Instant FEBRUARY = Instant.parse("2026-02-01T00:00:00Z");
	private static final Instant MARCH = Instant.parse("2026-03-01T00:00:00Z");
	private static final Instant APRIL = Instant.parse("2026-04-01T00:00:00Z");
	private static final Instant MAY = Instant.parse("2026-05-01T00:00:00Z");
	private static final IndexSchema V1 = new IndexSchema("document", "v1");
	private static final IndexSchema V2 = new IndexSchema("document", "v2");

	@Test
	void intersectsScopeAndExecutesOneQueryPerSchema(VertxTestContext testContext) {
		NamedFilter reportFilter = new NamedFilter("report");
		NamedFilter contextFilter = new NamedFilter("context");
		NamedFilter userFilter = new NamedFilter("user");
		TestDefinition definition = new TestDefinition(
			Set.of(V1, V2),
			ReportQueryScope.builder()
				.withFromInclusive(JANUARY)
				.withToExclusive(APRIL)
				.withMandatoryFilter(reportFilter)
				.withMaxLimit(50)
				.build(),
			ReportQueryPlan.builder()
				.withFromInclusive(JANUARY_15)
				.withToExclusive(MARCH)
				.withFilter(userFilter)
				.withLimit(30)
				.withQuery(new TestQuery("hits"))
				.build()
		);
		AtomicReference<PublishedIndexQuery> resolvedQuery = new AtomicReference<>();
		PublishedIndexResolver resolver = query -> {
			resolvedQuery.set(query);
			return Future.succeededFuture(List.of(
				published(1, 11, "index-1", V1),
				published(2, 12, "index-2", V1),
				published(3, 13, "index-3", V2)
			));
		};
		List<DocumentQueryExecution> executions = new ArrayList<>();
		DocumentQueryProvider provider = execution -> {
			executions.add(execution);
			return Future.succeededFuture(new TestProviderResult(execution.indexes().size()));
		};
		DefaultReportsFacade facade = new DefaultReportsFacade(
			catalog(definition),
			resolver,
			provider
		);
		ReportExecutionContext context = ReportExecutionContext.builder()
			.withScope(ReportQueryScope.builder()
				.withFromInclusive(FEBRUARY)
				.withToExclusive(MAY)
				.withMandatoryFilter(contextFilter)
				.withMaxLimit(20)
				.build())
			.build();

		facade.execute(context, request())
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(2, result.getPayload().getInteger("groups"));
				assertEquals(3, result.getPayload().getInteger("indexes"));
				assertEquals(FEBRUARY, resolvedQuery.get().fromInclusive());
				assertEquals(MARCH, resolvedQuery.get().toExclusive());
				assertEquals(2, executions.size());
				assertEquals(List.of("index-1", "index-2"), executions.get(0).indexes()
					.stream().map(PublishedIndex::indexName).toList());
				assertEquals(List.of("index-3"), executions.get(1).indexes()
					.stream().map(PublishedIndex::indexName).toList());
				for (DocumentQueryExecution execution : executions) {
					assertEquals(FEBRUARY, execution.fromInclusive());
					assertEquals(MARCH, execution.toExclusive());
					assertEquals(20, execution.limit());
					AllOfQueryFilter filter = assertInstanceOf(
						AllOfQueryFilter.class,
						execution.filter()
					);
					assertEquals(
						List.of(reportFilter, contextFilter, userFilter),
						filter.filters()
					);
				}
				testContext.completeNow();
			})));
	}

	@Test
	void emptyEffectiveScopeSkipsResolutionAndProvider(VertxTestContext testContext) {
		AtomicBoolean resolverCalled = new AtomicBoolean();
		AtomicBoolean providerCalled = new AtomicBoolean();
		TestDefinition definition = new TestDefinition(
			Set.of(V1),
			ReportQueryScope.builder()
				.withFromInclusive(JANUARY)
				.withToExclusive(FEBRUARY)
				.build(),
			ReportQueryPlan.builder()
				.withFromInclusive(MARCH)
				.withToExclusive(APRIL)
				.withQuery(new TestQuery("hits"))
				.build()
		);
		DefaultReportsFacade facade = new DefaultReportsFacade(
			catalog(definition),
			query -> {
				resolverCalled.set(true);
				return Future.succeededFuture(List.of());
			},
			execution -> {
				providerCalled.set(true);
				return Future.succeededFuture(new TestProviderResult(0));
			}
		);

		facade.execute(ReportExecutionContext.builder().build(), request())
			.onComplete(testContext.succeeding(result -> testContext.verify(() -> {
				assertEquals(0, result.getPayload().getInteger("groups"));
				assertEquals(0, result.getPayload().getInteger("indexes"));
				assertTrue(!resolverCalled.get());
				assertTrue(!providerCalled.get());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsUnsupportedSchemaBeforeProviderExecution(VertxTestContext testContext) {
		AtomicBoolean providerCalled = new AtomicBoolean();
		TestDefinition definition = definition(Set.of(V1));
		DefaultReportsFacade facade = new DefaultReportsFacade(
			catalog(definition),
			query -> Future.succeededFuture(List.of(published(1, 1, "index-v2", V2))),
			execution -> {
				providerCalled.set(true);
				return Future.succeededFuture(new TestProviderResult(1));
			}
		);

		facade.execute(ReportExecutionContext.builder().build(), request())
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertInstanceOf(UnsupportedReportSchemaException.class, error);
				assertTrue(!providerCalled.get());
				testContext.completeNow();
			})));
	}

	@Test
	void catalogRejectsDuplicateReportNames() {
		TestDefinition definition = definition(Set.of(V1));
		assertThrows(IllegalArgumentException.class, () -> DefaultReportCatalog.builder()
			.withDefinitions(List.of(definition, definition))
			.build());
	}

	@Test
	void identifiesRequestCodecValidationSeparatelyFromProviderFailures() {
		DefaultReportsFacade facade = new DefaultReportsFacade(
			catalog(definition(Set.of(V1))),
			query -> Future.succeededFuture(List.of()),
			execution -> Future.succeededFuture(new TestProviderResult(0))
		);

		assertThrows(InvalidReportRequestException.class, () -> facade.execute(
			ReportExecutionContext.builder().build(),
			ReportExecutionRequest.builder()
				.withReportName("summary")
				.withParameters(new JsonObject().put("invalid", true))
				.build()
		));
	}

	private TestDefinition definition(Set<IndexSchema> schemas) {
		return new TestDefinition(
			schemas,
			ReportQueryScope.builder().build(),
			ReportQueryPlan.builder().withQuery(new TestQuery("hits")).build()
		);
	}

	private DefaultReportCatalog catalog(TestDefinition definition) {
		return DefaultReportCatalog.builder().withDefinitions(List.of(definition)).build();
	}

	private ReportExecutionRequest request() {
		return ReportExecutionRequest.builder()
			.withReportName("summary")
			.withParameters(new JsonObject().put("value", "request"))
			.build();
	}

	private PublishedIndex published(
		int indexerId,
		int targetId,
		String name,
		IndexSchema schema
	) {
		return PublishedIndex.builder()
			.withIndexerId(indexerId)
			.withTargetId(targetId)
			.withIndexName(name)
			.withSchemaName(schema.name())
			.withSchemaVersion(schema.version())
			.build();
	}

	private record NamedFilter(String name) implements QueryFilter {
	}

	private record TestQuery(String capability) implements DocumentQuery {
	}

	private record TestProviderResult(int indexes) implements DocumentQueryResult {
	}

	private record TestRequest(String value) {
	}

	private record TestReportResult(int groups, int indexes) {
	}

	private static final class TestDefinition
		implements ReportDefinition<TestRequest, TestReportResult> {
		private final ReportDescriptor descriptor;
		private final ReportQueryPlan plan;

		private TestDefinition(
			Set<IndexSchema> schemas,
			ReportQueryScope scope,
			ReportQueryPlan plan
		) {
			descriptor = ReportDescriptor.builder()
				.withName("summary")
				.withTargetName("documents")
				.withScope(scope)
				.withSupportedSchemas(schemas)
				.build();
			this.plan = plan;
		}

		@Override
		public ReportDescriptor descriptor() {
			return descriptor;
		}

		@Override
		public ReportRequestCodec<TestRequest> requestCodec() {
			return new ReportRequestCodec<>() {
				@Override
				public TestRequest decode(JsonObject parameters) {
					if (parameters.getBoolean("invalid", false)) {
						throw new IllegalArgumentException("invalid parameters");
					}
					return new TestRequest(parameters.getString("value"));
				}

				@Override
				public JsonObject encode(TestRequest request) {
					return new JsonObject().put("value", request.value());
				}
			};
		}

		@Override
		public ReportResultCodec<TestReportResult> resultCodec() {
			return new ReportResultCodec<>() {
				@Override
				public TestReportResult decode(JsonObject payload) {
					return new TestReportResult(
						payload.getInteger("groups"),
						payload.getInteger("indexes")
					);
				}

				@Override
				public JsonObject encode(TestReportResult result) {
					return new JsonObject()
						.put("groups", result.groups())
						.put("indexes", result.indexes());
				}
			};
		}

		@Override
		public ReportQueryPlan plan(
			TestRequest request,
			ReportExecutionContext context
		) {
			return plan;
		}

		@Override
		public TestReportResult decode(DocumentQueryResults results) {
			return new TestReportResult(
				results.groups().size(),
				results.groups().stream()
					.map(DocumentQueryGroupResult::result)
					.map(TestProviderResult.class::cast)
					.mapToInt(TestProviderResult::indexes)
					.sum()
			);
		}
	}
}
