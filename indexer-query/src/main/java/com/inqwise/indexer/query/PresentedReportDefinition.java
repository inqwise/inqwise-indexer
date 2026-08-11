package com.inqwise.indexer.query;

import com.inqwise.indexer.query.presentation.ReportPresentation;

public interface PresentedReportDefinition<Q, R> extends ReportDefinition<Q, R> {
	ReportPresentation presentation();
}
