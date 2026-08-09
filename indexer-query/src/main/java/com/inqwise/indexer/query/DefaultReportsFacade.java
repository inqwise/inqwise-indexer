package com.inqwise.indexer.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.inqwise.indexer.publication.PublishedIndex;
import com.inqwise.indexer.publication.PublishedIndexQuery;
import com.inqwise.indexer.publication.PublishedIndexResolver;
import com.inqwise.indexer.query.service.ReportExecutionRequest;
import com.inqwise.indexer.query.service.ReportExecutionResult;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public final class DefaultReportsFacade implements ReportsFacade {
	private final ReportCatalog reports;
	private final PublishedIndexResolver publishedIndexes;
	private final DocumentQueryProvider queryProvider;

	public DefaultReportsFacade(
		ReportCatalog reports,
		PublishedIndexResolver publishedIndexes,
		DocumentQueryProvider queryProvider
	) {
		this.reports = Objects.requireNonNull(reports, "reports");
		this.publishedIndexes = Objects.requireNonNull(publishedIndexes, "publishedIndexes");
		this.queryProvider = Objects.requireNonNull(queryProvider, "queryProvider");
	}

	@Override
	public Future<ReportExecutionResult> execute(
		ReportExecutionContext context,
		ReportExecutionRequest request
	) {
		Objects.requireNonNull(context, "context");
		Objects.requireNonNull(request, "request");
		ReportDefinition<?, ?> definition = reports.find(request.getReportName())
			.orElseThrow(() -> new ReportNotFoundException(request.getReportName()));
		return executeTyped(definition, context, request.getParameters());
	}

	private <Q, R> Future<ReportExecutionResult> executeTyped(
		ReportDefinition<Q, R> definition,
		ReportExecutionContext context,
		JsonObject parameters
	) {
		ReportDescriptor descriptor = Objects.requireNonNull(
			definition.descriptor(),
			"definition.descriptor"
		);
		Q request = decodeRequest(definition, parameters);
		ReportQueryPlan plan = Objects.requireNonNull(
			definition.plan(request, context),
			"definition.plan"
		);

		Instant fromInclusive = latest(
			descriptor.scope().fromInclusive(),
			context.scope().fromInclusive(),
			plan.fromInclusive()
		);
		Instant toExclusive = earliest(
			descriptor.scope().toExclusive(),
			context.scope().toExclusive(),
			plan.toExclusive()
		);
		int effectiveLimit = Math.min(
			plan.limit(),
			Math.min(descriptor.scope().maxLimit(), context.scope().maxLimit())
		);
		QueryFilter effectiveFilter = QueryFilters.allOf(
			descriptor.scope().mandatoryFilter(),
			context.scope().mandatoryFilter(),
			plan.filter()
		);

		if (!fromInclusive.isBefore(toExclusive)) {
			return encode(definition, DocumentQueryResults.builder()
				.withFromInclusive(fromInclusive)
				.withToExclusive(toExclusive)
				.withEffectiveFilter(effectiveFilter)
				.withEffectiveLimit(effectiveLimit)
				.withGroups(List.of())
				.build());
		}

		PublishedIndexQuery indexQuery = PublishedIndexQuery.builder()
			.withTargetName(descriptor.targetName())
			.withFromInclusive(fromInclusive)
			.withToExclusive(toExclusive)
			.build();
		return publishedIndexes.resolvePublishedIndexes(indexQuery)
			.compose(indexes -> executeGroups(
				definition,
				descriptor,
				plan,
				fromInclusive,
				toExclusive,
				effectiveFilter,
				effectiveLimit,
				indexes
			));
	}

	private <Q, R> Q decodeRequest(
		ReportDefinition<Q, R> definition,
		JsonObject parameters
	) {
		try {
			return Objects.requireNonNull(
				Objects.requireNonNull(
					definition.requestCodec(),
					"definition.requestCodec"
				).decode(parameters.copy()),
				"decoded request"
			);
		} catch (IllegalArgumentException | NullPointerException error) {
			throw new InvalidReportRequestException(error.getMessage(), error);
		}
	}

	private <Q, R> Future<ReportExecutionResult> executeGroups(
		ReportDefinition<Q, R> definition,
		ReportDescriptor descriptor,
		ReportQueryPlan plan,
		Instant fromInclusive,
		Instant toExclusive,
		QueryFilter effectiveFilter,
		int effectiveLimit,
		List<PublishedIndex> indexes
	) {
		Map<IndexSchema, List<PublishedIndex>> groups = new LinkedHashMap<>();
		for (PublishedIndex index : indexes) {
			IndexSchema schema = IndexSchema.builder()
				.withName(index.schemaName())
				.withVersion(index.schemaVersion())
				.build();
			if (!descriptor.supports(schema)) {
				return Future.failedFuture(
					new UnsupportedReportSchemaException(descriptor.name(), schema)
				);
			}
			groups.computeIfAbsent(schema, ignored -> new ArrayList<>()).add(index);
		}

		Future<List<DocumentQueryGroupResult>> executed = Future.succeededFuture(
			new ArrayList<>()
		);
		for (Map.Entry<IndexSchema, List<PublishedIndex>> group : groups.entrySet()) {
			DocumentQueryExecution execution = DocumentQueryExecution.builder()
				.withReportName(descriptor.name())
				.withTargetName(descriptor.targetName())
				.withSchema(group.getKey())
				.withIndexes(group.getValue())
				.withFromInclusive(fromInclusive)
				.withToExclusive(toExclusive)
				.withFilter(effectiveFilter)
				.withLimit(effectiveLimit)
				.withQuery(plan.query())
				.build();
			executed = executed.compose(results -> queryProvider.execute(execution)
				.map(result -> {
					results.add(DocumentQueryGroupResult.builder()
						.withSchema(group.getKey())
						.withResult(Objects.requireNonNull(result, "queryProvider result"))
						.build());
					return results;
				}));
		}

		return executed.compose(results -> encode(definition, DocumentQueryResults.builder()
			.withFromInclusive(fromInclusive)
			.withToExclusive(toExclusive)
			.withEffectiveFilter(effectiveFilter)
			.withEffectiveLimit(effectiveLimit)
			.withGroups(results)
			.build()));
	}

	private <Q, R> Future<ReportExecutionResult> encode(
		ReportDefinition<Q, R> definition,
		DocumentQueryResults results
	) {
		R reportResult = Objects.requireNonNull(
			definition.decode(results),
			"definition result"
		);
		JsonObject payload = Objects.requireNonNull(
			definition.resultCodec(),
			"definition.resultCodec"
		).encode(reportResult);
		return Future.succeededFuture(ReportExecutionResult.builder()
			.withPayload(Objects.requireNonNull(payload, "encoded result"))
			.build());
	}

	private static Instant latest(Instant... values) {
		Instant latest = Objects.requireNonNull(values[0], "value");
		for (int index = 1; index < values.length; index++) {
			Instant value = Objects.requireNonNull(values[index], "value");
			if (value.isAfter(latest)) {
				latest = value;
			}
		}
		return latest;
	}

	private static Instant earliest(Instant... values) {
		Instant earliest = Objects.requireNonNull(values[0], "value");
		for (int index = 1; index < values.length; index++) {
			Instant value = Objects.requireNonNull(values[index], "value");
			if (value.isBefore(earliest)) {
				earliest = value;
			}
		}
		return earliest;
	}
}
