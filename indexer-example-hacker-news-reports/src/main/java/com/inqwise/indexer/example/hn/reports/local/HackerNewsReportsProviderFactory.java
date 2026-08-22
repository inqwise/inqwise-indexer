package com.inqwise.indexer.example.hn.reports.local;

import java.util.Map;

import com.inqwise.indexer.example.hn.reports.HackerNewsReportCatalog;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportConstants;
import com.inqwise.indexer.query.ConsumerReportExecutionContextResolver;
import com.inqwise.indexer.query.ReportExecutionContext;
import com.inqwise.indexer.query.provider.DefaultReportsProvider;
import com.inqwise.indexer.query.provider.ReportsProvider;
import com.inqwise.indexer.query.provider.ReportsProviderContext;
import com.inqwise.indexer.query.provider.ReportsProviderFactory;

public final class HackerNewsReportsProviderFactory implements ReportsProviderFactory {
	public static final String ID = "hacker-news";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public ReportsProvider create(ReportsProviderContext context) {
		return DefaultReportsProvider.builder()
			.withCatalog(HackerNewsReportCatalog.create())
			.withService(LocalHackerNewsReports.create(
				context.publishedIndexes(),
				context.documents(),
				new ConsumerReportExecutionContextResolver(Map.of(
					HackerNewsReportConstants.CONSUMER_NAME,
					ReportExecutionContext.builder().build()
				)),
				context.monitor()
			))
			.build();
	}
}
