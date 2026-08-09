package com.inqwise.indexer.query;

public interface ReportDefinition<Q, R> {
	ReportDescriptor descriptor();

	ReportRequestCodec<Q> requestCodec();

	ReportResultCodec<R> resultCodec();

	ReportQueryPlan plan(Q request, ReportExecutionContext context);

	R decode(DocumentQueryResults results);
}
