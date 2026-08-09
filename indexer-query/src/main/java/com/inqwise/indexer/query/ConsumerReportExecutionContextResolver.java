package com.inqwise.indexer.query;

import java.util.Map;
import java.util.Objects;

import com.inqwise.indexer.query.service.ReportCaller;
import com.inqwise.indexer.query.service.ReportExecutionRequest;

import io.vertx.core.Future;

public final class ConsumerReportExecutionContextResolver
	implements ReportExecutionContextResolver {

	private final Map<String, ReportExecutionContext> consumerScopes;

	public ConsumerReportExecutionContextResolver(
		Map<String, ReportExecutionContext> consumerScopes
	) {
		this.consumerScopes = Map.copyOf(Objects.requireNonNull(
			consumerScopes,
			"consumerScopes"
		));
	}

	@Override
	public Future<ReportExecutionContext> resolve(
		ReportCaller caller,
		ReportExecutionRequest request
	) {
		if (caller == null) {
			return Future.failedFuture(
				new InvalidReportRequestException("Trusted report caller is required")
			);
		}
		ReportExecutionContext context = consumerScopes.get(caller.getConsumerName());
		if (context == null) {
			return Future.failedFuture(new InvalidReportRequestException(
				"Unknown report consumer: " + caller.getConsumerName()
			));
		}
		return Future.succeededFuture(context);
	}
}
